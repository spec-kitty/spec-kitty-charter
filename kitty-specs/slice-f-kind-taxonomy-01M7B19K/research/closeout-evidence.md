# Closeout evidence: slice F

This is the closeout record of mission `slice-f-kind-taxonomy-01M7B19K` (spec SC-006; issue-tracker.md, Rule 2). It
names, for each issue the slice delivers, the file or scenario of the closeout pull request
(`slice-f-537-kind-taxonomy` into `main`) that shows the behavior. The file names are those of the build through
WP10. Nothing here has been posted to GitHub: the orchestrator posts the comments and ticks at landing.

Every `.feature` path is under `contracts/charter/scenarios/`, every planted pack under
`service/packs/src/test/resources/planted/`, and every test class under its module's `src/test/java`.

## The pull request body

```text
Closes #112
Closes #514
Closes #520
Closes #515
Closes #533
Closes #350
Refs #513 (narrowed, stays open), #94, #537, #49, #154, #337, #355, #417, #433, #447, #559.
```

The six close on merge. If a pull request does not close one, close it as `completed` with its comment below.

## Evidence

| Issue | What the slice delivers (spec) | Files and scenarios that show it |
|---|---|---|
| **#112** R18, the kind taxonomy read | FR-001 to FR-003, User Story 1: `GET /offering/kinds` answers every kind and every relation with its allowed endpoints, from the model, with no core org facts | Operation `getKindTaxonomy`: `contracts/charter/paths/offering_kinds.yaml`, schemas `KindTaxonomy`, `KindRow`, `RelationRow`, `EndpointPair`, `TaxonomyKind`, `TaxonomyRelation`, example `contracts/charter/examples/KindTaxonomy.model.yaml`. Scenarios: `offering.feature` "The kind taxonomy lists every kind and every relation, each relation with the kinds it may connect" and "The kind taxonomy answers 304 to an If-None-Match that matches its ETag, and sends the ETag again"; `root.feature` "The kinds link of the root answers the kind taxonomy". Parity with skewed positive controls: `VocabularyEqualityTest` (`theServedKindTaxonomyIsTheDomainsKindsAndRelationsAndTheFileKindTable`, and the four `theTaxonomyCheckFailsWhen…` controls); `OfferingControllerTest` (`theContractsExampleIsTheServedTaxonomy`, `theKindTaxonomyIsTaggedWithTheDigestOfItsBodyAndCarriesNoFingerprint`). Record: ADR-0077 decision 1. |
| **#514** retire `mission_step_contract`; substeps in the step | FR-004 to FR-006, FR-008, User Story 2: a step authors `substeps`; each candidate resolves and has its `scope` edge; the kind, its operation, schemas, code and file kinds are gone | Substeps: `mission-types.feature` "A step serves its substeps in authored order, each bare candidate linked to its read in the pack", the outline "A whole-pack registration whose substep candidate is <fault> is refused with <code>", "A substep candidate another pack of the closure does not hold makes the closure unreadable, beside the one it holds" and "A registration whose substep candidate the named base pack does not hold is refused, and its twin is accepted"; `effective-mission-types.feature` "A step's substeps are served under the activation, each held candidate linked to its read there". Planted packs `r35-substep-delegation-unresolved` and `r36-substep-delegation-unscoped` (`SubstepDelegationPlantsTest`); `SubstepTest`, `SubstepDelegationRuleTest`, `HeldCandidatesTest`. Retirement: `removed-routes.feature` outlines "A retired step contract route answers 404 kind_not_found (<path>)" and "A list filter that names the retired step contract is a 400 without a code (<path>)"; `contracts/packs/file-kinds.yaml` `retired:` rules for `**/*.step-contract.yaml` and `**/mission_step_contract.graph.yaml`, refused by name in `RetiredFilesTest`; no retired name in main sources or the contract: `RetiredNamesScanTest`. Content: the example pack's `missions/software-dev/software-dev.mission-type.yaml` (5 steps with substeps), its step-contract files and fragment deleted. Schema: `contracts/packs/schemas/mission-type.schema.json` (`Substep`, `SubstepInput`, `SubstepDelegation`); `mission-step-contract.schema.json` deleted. Record: ADR-0077 decisions 2 and 3; `contracts/charter/changes/2026-11-14-mission-step-substeps.md` and `2026-11-14-kind-taxonomy-and-step-authority-breaking.md`. |
| **#520** the mission-type schema and `template_set` | FR-009, FR-010 (and FR-011 of User Story 4): `action_sequence` and `template_set` are refused by name in a mission type; `template_set` leaves the project charter | Scenario outline `mission-types.feature` "A whole-pack registration whose mission type authors <field> is refused, naming the field" (`action_sequence`, `template_set`). Planted packs `r33-authored-action-sequence` and `r34-authored-template-set` (`MissionTypePlantsTest`). Project charter: `contracts/packs/schemas/charter.schema.json` without `template_set`, and `ProjectCharterYamlSchemaTest` "a catalog that carries the retired template_set is refused as an unknown field, by name (FR-010)". The complete type-to-step chain: `mission-types.feature` "A mission type that drops a step and keeps its requires edge is refused at registration, beside the one that drops both" and "A closure holding a pack whose mission type has an unlinked step is unreadable and names the type file"; planted `r32-mission-step-unlinked`; `MissionStepLinkRuleTest`. Record: ADR-0077 decisions 4 and 7. |
| **#515** each relation's endpoint kinds | FR-012 to FR-015, FR-017, User Stories 3 and 5: the endpoint table, typed constructors, refusal at read and write, retired relations | Table and types: `service/kernel/.../kernel/domain/EndpointTable.java`, `EndpointRule.java`, `KindRef`; `EndpointTableAgreementTest` (factories equal the table, a skewed table and a too-wide factory are caught), `EndpointRuleTest`, `KindRefTest`; ArchUnit `RELATIONSHIP_IS_BOUND` in `HexagonalArchitectureTest` with its known-bad control `RelationshipBuiltPastTheTable`. Write: `pack-write-refusals.feature` "putPackRelationship of an overrides edge from a directive to a tactic is 422 at /type, and between two directives it is written". Read and registration: `override-source-kind.feature` "A requires edge from the template is refused by the endpoint table, beside the same edge from the directive"; planted `r31-edge-overrides-across-kinds` (`EdgeEndpointPlantsTest`); `RelationshipEndpointRuleTest`; examples `CharterRefusal.relationship-endpoint-not-allowed.yaml`, `PackRegistrationStatus.relationship-endpoint-not-allowed.yaml`. Retired relations: `relationships.feature` "A retired relation is refused as an unknown type"; load fixture `shard-retired-relation`. Record: ADR-0077 decisions 5 and 6, the ADR-0064 amendment, `contracts/charter/changes/2026-11-13-relationship-endpoint-table.md`. |
| **#533** the step-contract read under an activation | FR-007: closed as moot, since the literal step-contract read no longer exists | ADR-0074 decision 12's gap (the literal `getEffectiveMissionStepContract` beside the generic pack read) is gone with the kind: the operation and `contracts/charter/paths/activations_activationId_artifacts_mission_step_contract_contractId.yaml` are deleted. `removed-routes.feature` "A retired step contract route answers 404 kind_not_found (<path>)" covers `/activations/a-1/artifacts/mission_step_contract/specify` and its `references`, and the pack twins; `RetiredNamesScanTest` keeps the name out. Record: ADR-0074 decision 12 amendment ("the step-contract gap (#533) is closed as moot"), ADR-0077 decision 3. |
| **#350** refuse a procedure edge to `action:` | FR-016: the endpoint table refuses `procedure -requires-> action` | Read refusal: `read-refusals.feature` "A closure whose pack has a procedure requiring a step's action is unreadable and names the graph document", with "The positive control: the same closure with the procedure requiring the directive is read". Load and validate mode: planted `r30-edge-procedure-requires-action` (`relationship_endpoint_not_allowed`, blocking, covers `#350`; `EdgeEndpointPlantsTest`). Domain: the edge of #350 does not compile through the typed factories; `RelationshipBuiltPastTheTable.procedureRequiresAStep` is the known-bad control `RELATIONSHIP_IS_BOUND` catches. Record: ADR-0077 decision 5 (ruling `DM-01M7B1Z30AV54T9WM2WR61EK30`). |

Each mapping matches the spec: #112 is User Story 1 and FR-001 to FR-003; #514 is User Story 2 and FR-004 to FR-006
and FR-008 (`DM-01M7B1YH1GF15RD0G4ZPSA8KW9`); #520 is FR-009 and FR-010 (User Story 4); #515 is FR-012 to FR-015
(`DM-01M7B1YTXP6QR1NQB784H4VX3W`); #533 is FR-007; #350 is FR-016 (`DM-01M7B1Z30AV54T9WM2WR61EK30`).

## Closing comments (one per issue, if a pull request does not close it)

- **#112:** "Delivered by #<PR> (slice F, ADR-0077 decision 1): `GET /offering/kinds` (`getKindTaxonomy`) answers
  every kind and every relation with its allowed endpoints, built from the kernel enums. See
  `contracts/charter/scenarios/offering.feature`, 'The kind taxonomy lists every kind and every relation, each relation
  with the kinds it may connect', and `VocabularyEqualityTest` for the parity with its skewed controls."
- **#514:** "Delivered by #<PR> (ADR-0077 decisions 2 and 3): a step authors `substeps`, and `mission_step_contract`
  is retired outright, refused by name as a retired file kind. See `mission-types.feature`, 'A step serves its substeps
  in authored order…', `removed-routes.feature`, 'A retired step contract route answers 404 kind_not_found', and
  `RetiredNamesScanTest`. The 'behind core #3420/#3419' ordering was not followed: charter.md forbids core-first
  ordering, and core aligns through the generated client. Open-packs follows with open-packs#TBD-fold-step-contracts."
- **#520:** "Delivered by #<PR> (ADR-0077 decisions 4 and 7): a mission type that authors `action_sequence` or
  `template_set` is refused by name, and `template_set` left `charter.schema.json`. See `mission-types.feature`,
  'A whole-pack registration whose mission type authors <field> is refused, naming the field', planted
  `r33-authored-action-sequence` and `r34-authored-template-set`, and `ProjectCharterYamlSchemaTest`."
- **#515:** "Delivered by #<PR> (ADR-0077 decision 5, the ADR-0064 amendment): each relation's source and target kinds
  are one table in the kernel, typed by kind markers and refused at read and write with
  `relationship_endpoint_not_allowed`. See `EndpointTableAgreementTest`, `pack-write-refusals.feature` and planted
  `r31-edge-overrides-across-kinds`."
- **#533:** "Closed as moot by #<PR> (ADR-0077 decision 3; ADR-0074 decision 12 amended): the step-contract kind and
  `getEffectiveMissionStepContract` no longer exist, so there is no gap left between that read and the generic pack
  read. See `removed-routes.feature`, 'A retired step contract route answers 404 kind_not_found (<path>)'."
- **#350:** "Delivered by #<PR> (ADR-0077 decision 5): the endpoint table refuses a procedure edge whose target is an
  action. See `read-refusals.feature`, 'A closure whose pack has a procedure requiring a step's action is unreadable
  and names the graph document', and planted `r30-edge-procedure-requires-action`."

## #513: narrowing comment (the issue stays open)

> Narrowed by slice F (#537, ADR-0077 decision 2; operator ruling `DM-01M7B1YH1GF15RD0G4ZPSA8KW9`, 2026-11-13).
> A mission-type step now authors `substeps`: a flat, ordered list, one level deep, each substep with `id`,
> `description`, `command`, `inputs`, `delegates_to` and `guidance`. The step contract is retired and its content is
> folded into the steps. What this issue still holds is only what slice F left out on purpose (spec C-008):
>
> - **recursion**: a substep that has substeps of its own;
> - **`DECOMPOSES_INTO`**: a relation from a step to its parts. It does not exist yet, and when it is added it needs a
>   row of the endpoint table (ADR-0077 decision 5) before it can be authored.
>
> The title and body should be edited to this scope in the same pass (issue-tracker.md, Rule 6).

## #94: tick

In epic #94's list of cutover reads, tick **R18, the kind taxonomy**: "served at `GET /offering/kinds`
(`getKindTaxonomy`), not deferred to the merge minor (#112, closed by #<PR>, ADR-0077 decision 1)". The step-contract
read gap of ADR-0074 decision 12 (#533) is closed as moot in the same pull request.

## #537: ticks

Tick each child of slice F, and close #537 when the pull request merges, since every child is then closed:

- [x] #112 R18, the kind taxonomy read
- [x] #514 retire `mission_step_contract`, substeps in the step
- [x] #520 the mission-type schema, `template_set` dropped
- [x] #515 each relation's endpoint kinds
- [x] #533 the step-contract read, closed as moot
- [x] #350 refuse a procedure edge to `action:` (included by ruling `DM-01M7B1Z30AV54T9WM2WR61EK30`)
- [ ] open-packs follow-up, not a charter child: open-packs#TBD-fold-step-contracts, open-packs#TBD-step-requires-edges,
  open-packs#TBD-delegation-scope-edges, open-packs#TBD-pre-review-guard and open-packs#TBD-validator-and-docs (drafts in
  `research/open-packs-drafts/`), which the orchestrator files in `spec-kitty/spec-kitty-open-packs`

## Open-packs follow-up and its proof

The drafts are in [`open-packs-drafts/`](open-packs-drafts/README.md). On a scratch copy of open-packs `1692a3b` with
drafts 1 to 3 applied, `research/edge-census.py` counted 1,436 open-packs edges, and `open-packs-drafts/census-check.py`
over that census and tree reported 0 refused edges, 0 unlinked steps, 0 unresolved and 0 unscoped candidates, and 0
retired files (spec SC-005).
