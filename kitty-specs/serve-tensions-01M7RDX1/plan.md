# Implementation Plan: Serve tensions

**Branch**: `kitty/mission-serve-tensions-01M7RDX1` (lands on `feat/602-tensions`, PR #606 to `main`) | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/serve-tensions-01M7RDX1/spec.md`; ADR-0045 as accepted 2026-11-18.

## Summary

A tension becomes a relation of the one catalogue (`RelationType.TENSION`, token `tension`, bidirectional,
`G ↔ G`). Its identity is the unordered pair plus its pair class, so it stands beside the pair's one structural
relationship. The kernel `Tension` value, the `in_tension_with` spelling and every special path for them are removed.
Once typed, tension edges flow through the existing edge path: the composition facts, the merged `EdgeIndex` with its
layer-override resolution, and the registration upsert. The walks keep their explicit relation sets, so no walk
follows a tension. On top of that, one application-level projection, `TensionsInForce`, computes the tensions in force
and their reconcilers. The effective graph, the action context and the new `getActivationTensions` read all answer
from it.

## Engineering Alignment

- **The intent is the operator's rulings** (decision DM-01M7RDXACK9SVR5E3G21BD9KW2). No planning question was asked
  of the operator beyond F1 to F3, which are answered; the three calls without a ruling are ADR-0045's open review
  items.
- **Invariant.** No walk, cascade, reach or governance read follows `tension`; the plan relies on their explicit
  relation sets (`CascadeRules.CASCADE_RELATIONS`, `EffectiveSet.HARD_RELATIONS`, `ActionContextWalk.Step`,
  `GovernanceWalk.Step`, `ReachRequest`) and pins it with a test.
- **Identity.** `Relationship.equals` and `hashCode` become pair plus class; `samePair` stays the pair test. Each pair
  user is classified: `RelationshipMerge`, `RelationshipRegistrationService` (`created`, the step-enhancement guards)
  and `RelationshipRemovalService.names` use the class.
- **Orientation.** Canonical is the smaller bare URN as `source`, judged without the pack qualifier.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain). OpenAPI 3.1 contract in `contracts/charter/`, and the packs contract
in `contracts/packs/`.
**Primary Dependencies**: no new dependency (supply chain: no dependency is added, upgraded or removed, so
DIRECTIVE_051's controls have nothing to judge).
**Storage**: pack files on disk; `registered.graph.yaml` written by the registration path (`RelationshipShard`,
`GraphShardWriter`).
**Testing**: the tests live in the module of their subject (ADR-0062).
- Kernel unit tests for the relation, the endpoint row and the pair-class identity.
- `:packs:test` for the binding, the consistency rules, the round trip, the merge, the registration and
  `TensionsInForce`.
- `:activations:test` for the reads, the mapper, the tags and the links.
- `@contract` scenarios through `:deployable:test`, over one new example pack (FR-012) plus the shared
  every-operation, read-refusal, unexpected-failure and activation-links rows; the switch gate (ADR-0059 D1).
- The whole-pack round trip (`:pack-validation:wholePackRoundTrip`) over charter's example packs.
- `./gradlew verifyAll --max-workers=1`, in the foreground, one run at a time; the docs checks.
**Target Platform**: the charter service (Linux container and native image).
**Project Type**: multi-module service (`service/*`).
**Performance Goals**: no new pass over the packs: `TensionsInForce` reads the merged links already built for the
graph (NFR-004).
**Constraints**: the canonical model with no legacy reader (C-001); `0.2.0-SNAPSHOT` with `### Breaking` entries
(C-002); signed commits, rebase only; no write to open-packs (C-004); keep #108's neighbouring lines (C-005).
**Scale/Scope**: about 45 production files and 40 test files, 6 schemas, 1 path, about 8 scenario files.

## Charter Check

| Charter rule | Status |
| --- | --- |
| The charter's model is canonical; no legacy reader | Pass. `in_tension_with` and `Tension` are removed outright; open-packs follows through its own PR. |
| Tests and examples carry only the canonical shape | Pass. `in_tension_with`, reversed and duplicate tensions appear only as refusal fixtures. |
| Contract: 0.x may break on a minor, with `### Breaking` | Pass. Fragments for the rename, the stricter loads and the removal parameter (breaking); the new read and fields (added). |
| No unverified dependency | Pass. None added. |
| The whole-pack round trip over the example packs | Pass. The new example pack's tensions round-trip. |
| Adversarial squad at the plan point and before merge | Plan point: [research/squad-plan.md](research/squad-plan.md). Before merge: one squad over the diff. |
| Issues stay small | Pass. Nothing new is filed here; the open-packs draft goes to the orchestrator. `Closes #602`. |

No violations.

## Project Structure

### Documentation (this mission)

```
kitty-specs/serve-tensions-01M7RDX1/
├── spec.md
├── plan.md
├── research.md
├── research/squad-plan.md
├── research/draft-open-packs-tension-rename.md
├── data-model.md
├── quickstart.md
├── occurrence_map.yaml
├── contracts/tensions.openapi.yaml
└── tasks.md            (by /spec-kitty.tasks)
```

### Source Code (repository root)

```
service/kernel/.../kernel/domain/        RelationType, EndpointTable, EndpointRule, Relationship, DoctrineGraph, NodeKind; Tension removed
service/packs/.../packs/domain/rules/    GraphBinding, GraphRules (+ tension consistency), PackRulesets
service/packs/.../packs/domain/          PackCodes, RelationshipEndpointRule, PackEdgeRepository
service/packs/.../packs/application/     RelationshipMerge, RelationshipRegistrationService, RelationshipRemovalService,
                                         KindTaxonomyReadService, GraphProjection, ClosureView, TensionsInForce (new)
service/packs/.../packs/infra/storage/   GraphShardWriter, RelationshipShard, DomainRoundTrip, DomainRepositoryService,
                                         CompositionFactsBuilder, WholePackStore
service/packs/.../packs/infra/api/       RelationshipWire, PackRemovalController
service/packs/src/testFixtures/resources/example-packs/tensions/   (new example pack)
service/activations/...                  EffectiveReadService, EffectiveContext, GraphSnapshot, EffectiveTag,
                                         ClosureViewSession, PacksSession, ActivationController, ActivationMapper,
                                         EffectiveReadMapper
contracts/charter/                       schemas (Relation, EffectiveGraphEdge, EffectiveActionContext, ContextTension,
                                         ActivationTensions, TensionFinding, TaxonomyRelation, RelationRow, KindTaxonomy),
                                         paths (activations_activationId_tensions, packs_packId_relationships), openapi.yaml,
                                         examples, scenarios, switch-gate.yaml, headers, changes/
contracts/packs/schemas/                 graph-shard.schema.json, org-fragment.schema.json
contracts/lint/ruleset.yaml              link relation `tensions`
docs/                                    adr, api/service-contract.md, conformance/divergences.yaml (+ register),
                                         development/hypermedia-hateoas.md, _meta/terminology.yaml
```

## Complexity Tracking

| Choice | Why | Simpler alternative rejected |
| --- | --- | --- |
| One `TensionsInForce` projection shared by three reads | The three reads must agree (FR-006, NFR-001) | Computing per read would let the graph and the finding disagree on a reconciler |
| `EffectiveTag.DOMAIN` bumped to v4 | Bodies change for the same inputs (graph, context, activation links) | Keeping v3 would serve a stale 304 to a client holding an old tag |

## Implementation Concern Map

### IC-01 — Tension is a relation of the model (kernel and storage)

- **Purpose**: one model of an edge: `RelationType.TENSION`, the pair-class identity, the `tension` spelling in files
  and schemas, and the removal of `Tension` and every special path.
- **Relevant requirements**: FR-001, FR-002, FR-003, C-001.
- **Affected surfaces**: kernel `RelationType`, `EndpointTable` (row, drop the separate set), `EndpointRule`
  (`bindTension` out), `Relationship` (identity, `tension(...)` factory, `sameSlot`), `DoctrineGraph`, `NodeKind`;
  packs `GraphBinding`, `RelationshipEndpointRule`, `GraphShardWriter`, `RelationshipShard`, `DomainRoundTrip`,
  `DomainRepositoryService` (skip out), `CompositionFactsBuilder`, `WholePackStore`, `PackEdgeRepository` docs;
  `contracts/packs/schemas/*`, `contracts/charter/schemas/Relation.yaml`, `WireTokenParityTest`; the load fixture
  `shard-tension-binds-as-tension`; the registered-shard tension finding removed (it is FR-005's, but it lives in
  `GraphBinding`).
- **Sequencing/depends-on**: none. Everything else follows.
- **Risks**: the kernel change breaks compilation of packs until both land, so this concern is one WP. Tensions start
  reaching `EdgeIndex`, `GraphProjection`, `InboundReferences` and the references listing; GraphProjection must keep
  answering a valid body (the `Relation` enum gains `tension` in the same WP).

### IC-02 — Pack tension consistency

- **Purpose**: refuse a pack holding two tensions on one unordered pair, or a reversed tension.
- **Relevant requirements**: FR-004.
- **Affected surfaces**: `GraphRules` (a third rule over `read(contents)`), `PackCodes` (two codes,
  `tension_duplicated` and `tension_orientation`), the validate goldens, `planted/` verdicts, load fixtures. The codes
  reach the wire as violations of the existing refusals, so `CharterRefusalCode.yaml` is unchanged.
- **Sequencing/depends-on**: IC-01.
- **Risks**: the rule must judge bare URNs (qualifier stripped) and count across all shards and the fragment of the
  pack.

### IC-03 — Registration carries tensions

- **Purpose**: `putPackRelationship` with `type: tension`, stored canonically, kept through rewrites; removal by
  relation.
- **Relevant requirements**: FR-005.
- **Affected surfaces**: `RelationshipWire`, `RelationshipDraft`, `RelationshipRegistrationService` (`created`, the
  step-enhancement guards), `RelationshipMerge`, `RelationshipRemovalService` (`names`, `heldOutsideTheShard`),
  `PackRemovalController`, `paths/packs_packId_relationships.yaml` (the `relation` query parameter), the relationship
  scenarios.
- **Sequencing/depends-on**: IC-01.
- **Risks**: canonical storage when the smaller side is the qualified `to`; `PackRegistrationService.edgeKey` is
  orientation-sensitive.

### IC-04 — Taxonomy and references

- **Purpose**: the taxonomy row `tension`; references list tension edges; the walk never follows a tension.
- **Relevant requirements**: FR-011.
- **Affected surfaces**: `KindTaxonomyReadService` (drop the extra row; `HeldAs.TENSION` for `RelationType.TENSION`),
  `TaxonomyRelation.yaml`, `RelationRow.yaml`, `KindTaxonomy.yaml`, `offering_kinds.yaml`,
  `KindTaxonomy.model.yaml`, `offering.feature`, the references scenarios, a walk test.
- **Sequencing/depends-on**: IC-01.
- **Risks**: the taxonomy row moves from last to RelationType order; the scenario's index (`/relations/13`) changes.

### IC-05 — Tensions in force and the effective graph

- **Purpose**: the one projection of tensions in force with their reconcilers, and the graph's tension edges.
- **Relevant requirements**: FR-006, FR-007, FR-012 (the example pack), NFR-001, NFR-004.
- **Affected surfaces**: `TensionsInForce` (new, `packs.application`), `ClosureView`, `GraphProjection` (keep `when`,
  `reason` for tension; `reconciledBy`), `GraphSnapshot`, `ClosureViewSession.graphOf`, `EffectiveReadMapper.toGraph`,
  `EffectiveGraphEdge.yaml`, `EffectiveTag.DOMAIN` v4, the example pack `example-packs/tensions` with its
  activations, a new `tensions.feature`.
- **Sequencing/depends-on**: IC-01.
- **Risks**: canonicalize after override re-pointing; collapse across packs with the first in merge order; the ETag
  domain bump changes every effective tag (tests that pin tags).

### IC-06 — Tensions in the action context

- **Purpose**: `tensions` in `getEffectiveActionContext`, from the context's listed in-force artifacts.
- **Relevant requirements**: FR-008.
- **Affected surfaces**: `EffectiveContext`, `PacksSession`/`ClosureViewSession`, `EffectiveReadMapper.toContext`,
  `EffectiveActionContext.yaml`, new `ContextTension.yaml`, `examples/EffectiveActionContext.*`, scenarios.
- **Sequencing/depends-on**: IC-05.
- **Risks**: depth and suggestions both exclude a side; a mission type not in force answers `[]`.

### IC-07 — The activation's tensions read

- **Purpose**: `getActivationTensions` with findings, resolutions and links.
- **Relevant requirements**: FR-009, FR-010, C-003.
- **Affected surfaces**: the path file, `openapi.yaml`, `_index.yaml` (schemas, examples), `ActivationTensions.yaml`,
  `TensionFinding.yaml`, `EffectiveETag.yaml` and `CharterSourceFingerprint.yaml` op lists, `Activation.yaml` and its
  example, `ActivationMapper` (`tensions` link), `EffectiveTag.Read.TENSIONS`, `EffectiveReadService`,
  `ActivationController`, `EffectiveReadMapper`, `every-operation.feature`, `read-refusals.feature`,
  `unexpected-failure.feature`, `activation-links.feature`, `switch-gate.yaml`, `contracts/lint/ruleset.yaml`,
  `docs/development/hypermedia-hateoas.md` (relations `tensions`, `deactivate-source`, `deactivate-target`, `source`,
  `target`).
- **Sequencing/depends-on**: IC-05.
- **Risks**: shared files with #108 (append, keep both sides).

### IC-08 — Records and the open-packs draft

- **Purpose**: the docs and registers match the reads; the companion migration is drafted.
- **Relevant requirements**: FR-013, FR-014.
- **Affected surfaces**: `docs/api/service-contract.md`, `docs/conformance/divergences.yaml` and the register page,
  `docs/_meta/terminology.yaml`, `site/.contextive/definitions.yaml`, `contracts/charter/changes/2026-11-18-*.md`,
  `contracts/packs/README.md`, `research/draft-open-packs-tension-rename.md`.
- **Sequencing/depends-on**: IC-01 to IC-07.
- **Risks**: none beyond the docs checks.

### Sequence

IC-01 first. Then IC-02, IC-03, IC-04 and IC-05 can run in parallel. IC-06 and IC-07 follow IC-05. IC-08 comes last.

## Revisions after the plan-point squad and rulings G1 to G3 (2026-11-19)

These amend the concerns above; where they differ, this section wins. Dispositions: [research/squad-plan.md](research/squad-plan.md).

- **IC-01** also: public `Relationship.samePair` and `sameSlot` in the kernel; `DoctrineGraph.merge` upserts on the
  slot; `ArtifactRemovalService:175` filters by identity, not `contains`; `DomainRoundTrip.relationshipFields`
  compares `fromPack` and `toPack`; the `Relationship` and `CompositionFactsBuilder` javadocs describe the pair class;
  kernel equality tests for the PIT floor.
- **IC-02** also: the stored orientation of G1 (the source is owned by the pack; two owned sides smaller first) and
  `tension_self` (same bare URN on both sides).
- **IC-03** also: 422 `tension_duplicated` when the pair is already joined by a tension in another document of the
  pack; the wire refuses a self-tension; both owned sides written larger first are stored the other way round and
  the answer echoes the request; `deletePackRelationship` per pair class (400 unknown `relation`; 409
  `relationship_not_registered` naming `relation` when absent and an authored structural edge remains);
  `RelationshipRemovalService.names` and `heldOutsideTheShard` filter by class; removing an artifact takes its
  registered tensions with it on either side, and `InboundReferences` does not count a tension as a reference that
  blocks a removal (a tension is not a dependency).
- **IC-05** also:
  - `TensionsInForce` resolves symmetrically (G2) from the authored tensions and the layer merge, not through
    `EdgeIndex.merged`'s source-place rule; the graph takes tension edges only from it; `GraphProjection`'s distinct
    stays for the other relations; merge order is the closure's writer order, then document order.
  - `ClosureView.tensions(states)` public; `TensionSnapshot` in `activations.domain`; `GraphSnapshot.Edge` widened.
  - `EffectiveGraphEdge` documented `oneOf`; `reconciledBy` unique and sorted; `x-derived` on each property.
  - The example pack `tensions` joins `ExamplePacks.names()` and `LEVELS` at a writable level with an `open` policy;
    the enumeration tests (WholePackRoundTripTest, ExamplePacksComposeTest, ExamplePacksTest, PackDigestBaselineTest,
    WholePackExpectation, ProfileCorpusTest, ResolvedProfileCorpusTest, ManifestMembershipTest, ClosureViewTest,
    ExampleVocabulary*Test, MissionTypeCorpusTest) update their expectations, never narrow them.
  - `tensions.feature` holds the five activations inline; a graph step prints `reconciledBy`, `when` and `reason`.
  - `EffectiveTag.DOMAIN` v4 with `EffectiveTagTest` twins (v3 no longer matches); `EffectiveReadOperationBudgetTest`
    pins NFR-004; a permutation unit test pins NFR-001.
- **IC-06**: depth only governs `suggests`; the depth and suggestion cases are one scenario with depth 0 and 1.
- **IC-07** also: links `exclude-source`/`exclude-target` (PUT, If-Match) omitted for a locked or required side, and
  `effective-source`/`effective-target` (G3); `_links` is `$ref Links.yaml`, ActivationTensions carries `self`; its own
  `Read.TENSIONS`; every declared status asserted (400, 401, 403, 404, 304, 500), and its tag differs from the skipped
  report's.
- **IC-08** also: an ADR-0027 amendment note (pair class); the `### Breaking` / `### Added` / `### Changed` split of
  the contract fragment; the stale #101 row of `docs/api/service-contract.md`.
