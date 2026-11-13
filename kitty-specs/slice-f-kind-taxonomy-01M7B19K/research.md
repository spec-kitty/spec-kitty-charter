# Research: Slice F, the kind taxonomy and the mission-type schema

Grounding note for slice F ([#537](https://github.com/spec-kitty/spec-kitty-charter/issues/537)), written before the
spec on 2026-11-13 against `main` at `848170e`. Four investigations ran: the `mission_step_contract` footprint, the
mission-type schema, a census of relation endpoint kinds, and a collision map with slice C and epic #549. Two squads
ran after them, on similar surfaces and on tracked issues; their dispositions are in R11 and R12. The evidence is
filed under `research/`.

The operator ruled on 2026-11-13 that research, specify and plan run now. Implementation starts only after slice C
(#467) merges, or rebases onto it.

Status of each item: **Proposed** means it waits for the operator, and **Settled** means an ADR or ruling already
decides it. The open rulings are listed in R13.

## R1. Sources read

- Issues: #537 with its comment; children #112, #514, #520, #515 (with the 2026-11-12 ruling comment) and #533; epic #94 with
  its four comments; #447, #339 and #434; slice C #467; epic #549 with its handover comment, #554 and #556.
- ADR-0064 in full, with decision 13 and its amendment of 2026-11-12. ADR-0074 decisions 9 to 14. ADR-0028 and
  ADR-0044. ADR-0050 and ADR-0075 (with the 2026-11-13 amendment on PR #561) through the investigations.
- `docs/api/service-contract.md`, `docs/architecture/resolution-model.md:100-104` and
  `docs/plans/next-slices-2026-11-12.md`.
- Core, as evidence only (charter.md: core is not a consumer until incorporation):
  - `src/charter/offering/artifact_kinds.py` and `src/charter/offering/missions/models.py`;
  - ADR `3.x/2026-08-13-1-built-in-mission-subtree-stays-nested-retire-legacy-step-contracts.md`.
- Open-packs at the pinned `1692a3b`, cloned read-only to `../spec-kitty-open-packs`.

## R2. R18: the kind taxonomy served as data (#112)

**Today.** The kind facts are hand-kept in several places:

- `ArtifactKind` holds the token, the plural and `activatable`.
- `NodeKind` adds `ACTION` and `authoredInGraphs`.
- The contract repeats these as enums: `ArtifactKindToken`, `ActivatableKind`, `NodeKind`, `CascadeKind`, `Relation`.
  Each carries an `x-source` that points at the Java symbol.
- `resolution-model.md:100-104` promises "the kind table as data", which does not exist.
- `service-contract.md:286` and `:293` defer `/offering/kinds` "to the minor that brings merge".
- The 2026-10-20 re-scope on #94 named the path `/offering/kinds`.

**Core's facts do not all apply here.** Core's kind facts are `plural`, `effective_when_absent`,
`selection_overlayable` and `org_requirable`. The last three describe core's org-charter overlay and absent-key
default. The charter's model has neither:

- an absent activation key means none (DIV-203);
- the `org-charter.yaml` that held `required_<kind>` is retired (#431).

So those facts have no subject in the charter's model, and copying them would serve a shape the model does not hold.

**Proposed shape: `getKindTaxonomy`, `GET /offering/kinds`.** One read, with no merge dependency: the table is a
property of the model, not of the configured packs. So the deferral to the merge minor no longer applies.

The body holds two lists:

- **`kinds`.** One row per node kind, in `NodeKind` order. Each row has:
  - `kind`, the token;
  - `plural`;
  - `artifact`: false only for `action`, which is a node with no artifact;
  - `activatable`: whether an activation selects or denies it;
  - `authoredInGraphs`: false for `guard` and `lifecycle`;
  - `_links` to its pack list and activation list reads, where they exist.
- **`relations`.** One row per relation. Each row has:
  - `relation`;
  - `directed`;
  - `derived`: true for `implemented_by`;
  - `endpoints`, a list of `{source, target}` kind pairs. This is the R6 table: the read serves #515's rule as data,
    so a client never re-derives it.

Behavior of the read:

- The `ETag` is the contract version.
- The only refusals are the common ones (401, 403).
- Its `@contract` scenarios check it against `ArtifactKind`, `NodeKind` and `RelationType`. One fact has one source:
  the kind table is the model's, and the contract enums keep their `x-source`.

**Alternatives.**

- `/kinds` at the root. Rejected: the operator placed it under `/offering` on 2026-10-20.
- Two reads, `/offering/kinds` and `/offering/relations`. Not proposed: the endpoint table names kinds, so it is one
  taxonomy.
- Exempt, or out of the cutover (#112's other two options). Rejected: R18 is a read core makes today
  (37 activation imports, per #112).

**Open.** The operator confirms the path, the two lists, and the absence of core's three facts (Q1).

## R3. Retire `mission_step_contract` (#514)

**Footprint** (`research/footprint-mission-step-contract.md`):

- 88 Java files across kernel, web-transport, packs, missions-edge, activations, artifacts-edge and deployable.
  - 21 of them exist only for the kind: in packs, `MissionStepContract`, `MissionStepContractIdentifier`,
    `ContractStep`, `GateBinding`, `StepDelegation`, `StepInput` and the three storage classes; in missions-edge and
    activations, `StepContracts`, `PacksStepContracts`, `EffectiveStepContract*`, `StepContractView` and
    `EffectiveStepContractWinner`.
  - The others mention it in a switch, a map or a test.
- In the contract:
  - one operation: `getEffectiveMissionStepContract`;
  - six schemas: `MissionStepContract*` and `MissionStepDelegation*`;
  - `parameters/StepContractId`;
  - one response, one refusal code: `mission_step_contract_not_found`;
  - the kind's value in four kind enums;
  - the `step-contract` link on `MissionStep`;
  - `activatedMissionStepContracts` on `Preset`, and the `selected_`/`deactivated_` members;
  - three heavy scenario files.
- The pack schema `mission-step-contract.schema.json`, and the kind's entries in `graph-shard`, `org-fragment`,
  `pack-manifest`, `policy`, `preset`, `charter` and `file-kinds.yaml`.
- Fixtures:
  - charter's example `built-in` pack has 5 step-contract files and a 5-node, 0-edge graph fragment;
  - `packs/qa`, a synced copy, has 7 files;
  - the planted and digest fixtures have 4 more.
- Open-packs has 28 files (foundation 14, writing 7, qa 7) and two `mission_step_contract.graph.yaml` fragments,
  21 nodes and **0 edges**.

**The step is not a superset of the step contract.** A step contract's `steps[]` are **ordered substeps of one
action**. Each substep has a `command`, CLI `inputs`, `delegates_to: {kind, candidates[]}` and inline `guidance`. A
contract also has `gates[]`. The charter's `MissionStep` holds none of these. Open-packs carries:

- 144 delegation candidates: 59 directive, 36 tactic, 6 procedure, 1 paradigm and 1 toolguide delegations;
- one gate, in `review`.

What each part of the content becomes:

- **Delegations.** 72 of the 144 candidates already have a `scope` edge from the step's `action:<type>/<step>` node
  (or the mission type) to the candidate. The other 72 have none. List: `research/delegation-edge-coverage.md`.
- **The gate.** ADR-0054 decision 12 already rules its move: `spec-kitty-pre-review` becomes a guard on the
  foundation lifecycle's `in_progress -> for_review` transition. The lifecycle schema already has `transitions[].guards`,
  so it can move now. Core reading it is #339, core work at Post-cutover.
- **Substep order, commands and CLI inputs.** These are core's runtime surface. The charter model has no home for them
  until nested steps (#513, Post-cutover) or step transitions (#434, Post-cutover).

**Ordering conflict with #514's text.** #514 says the kind is retired only *behind* core #3420 and #3419, that is,
once core's `MissionStep` carries gates. charter.md (2026-11-10) says no "core first" ordering: the charter's model
is canonical, and core aligns through the generated client. **The charter rule overrides the issue text.** This is
flagged for the spec, and the spec does not wait on core.

**Proposed.** Retire the kind outright in this slice. The delegations and the gate move to existing homes, with no
new step field:

- each delegation becomes a `scope` edge from `action:<type>/<step>` to the candidate. The 72 missing edges are added
  by the open-packs migration;
- the gate becomes the lifecycle guard ADR-0054 decision 12 names;
- substep descriptions, commands and guidance move into the step's prompt and guidelines text in open-packs.

The substep structure is lost as structure until #513. **This is a model loss, and the operator rules on it (Q2).**

**Alternatives.**

- (b) Fold substeps into the mission-type `Step` as `substeps[{id, description, delegates_to, guidance}]`. This pulls
  #513 into the slice, a scope widening.
- (c) Keep the kind until #513 and #434. This contradicts #537's "done when".

**Content follow-through.** Drafts go to open-packs with `open-packs#TBD-<slug>` placeholders (charter.md):

- remove the 28 files and the 2 graph fragments;
- add the 72 `scope` edges;
- move the gate to the lifecycle guard;
- fold the substep text into the prompts;
- update the validator in `scripts/ci/validate_pack_schemas.py`;
- update the docs.

## R4. #533 becomes moot

The literal `getEffectiveMissionStepContract` goes with the kind, and so does the gap between it and the generic pack
read (ADR-0074 decision 12). #533 closes with that evidence. **Settled** by R3, if Q2 retires the kind.

## R5. The mission-type schema: steps are the single authoring surface (#520)

**Already delivered by ADR-0044** (`research/mission-type-schema.md`):

- `mission-type.schema.json` is closed at every level (`additionalProperties: false`), so an authored
  `action_sequence`, `template_set`, `in_action_sequence` or `sequence_index` is refused as an unknown key.
- `steps` is required with `minItems: 1`, and `MissionType` refuses an empty step list.
- The list order is the only order (ADR-0044 decisions 2 to 4). Core's "org-pack exception" for an authored
  `action_sequence` has no subject: there is no such field to except.
- `step.yaml` is a retired file kind (`file-kinds.yaml:291-294`).
- `RetiredMissionFieldNamesTest` keeps `in_action_sequence` and `sequence_index` out of the main sources.
- No mission type in charter or open-packs authors either field, and none has zero steps.

So #520's two schema questions are answered by the model as it stands:

- `action_sequence` and `template_set` are not authorable;
- every mission type has at least one step.

What is left:

1. **A named refusal, proven.** No `@contract` scenario or invalid example pack shows an authored `action_sequence`
   or `template_set` being refused. Add one invalid example pack per field (error-handling tests only), and a
   scenario.
2. **"Template" names only the kind.**
   - `template_set` is still a required field of `charter.schema.json`: `CharterCatalog` at lines 84 and 113, and
     `GovernanceCharterConfig` at line 357.
   - It is also used in the `charter.yaml` fixtures.
   - It names core's template-set selection, not a mission-type field.
   - Leftover wording: `RelationType.ENHANCES`'s javadoc says "keeping its action sequence".
   - Whether `charter.schema.json`'s `template_set` goes, or is renamed, is **open (Q3)**.
3. **The graph chain `mission_type → step → template`.**
   - It is carried as `mission_type -requires-> action` (21 edges) and `action -instantiates-> template` (11).
   - 47 steps across the six mission types have only 21 `requires` edges. A step with no `requires` edge from its
     type is missing from the chain.
   - Whether the chain must be complete, and whether the edge is derived from `steps` or authored, is **open (Q4)**.
     The model already knows the steps, so an authored copy would be a second source (ADR-0064's own argument).

**Proposed.** Do (1). Bring (2) and (3) to the operator with recommendations:

- (2): drop `template_set` from `charter.schema.json`. It is core's selection, and charter has no template sets.
- (3): derive `mission_type → action` from `steps`, and refuse an authored copy, as `implemented_by` is
  (ADR-0064 decision 7).

## R6. #515: each relation's endpoint kinds (ADR-0064 decision 13)

**Settled by ruling:**

- typed constructors in the domain (generics);
- a named refusal at read and at write;
- the table goes in an ADR-0064 amendment;
- #447 is settled by the same rule.

**Census** (`research/edge-endpoint-census.md`, with the CSV):

- 1,331 authored edges in open-packs and 48 in charter's example packs, by relation, source kind and target kind.
- Today the only endpoint check is `NodeKind.authoredInGraphs`, which refuses guard and lifecycle. It is applied at
  `PackPolicy.java:715, 729, 824`, `RelationshipWire.java:78` and `NodeRef.java:39, 51`.
- Besides that, only the step-enhancement rules exist (`StepEnhancementRules.java:182-215`).
- Nothing checks that `overrides`, `enhances` or `specializes_from` join one kind.

**Proposed endpoint table** (draft for the operator, Q5). G is the six guidance kinds: directive, tactic,
styleguide, toolguide, paradigm and procedure. R is {asset, template}.

| Relation | Source → target | Observed in open-packs |
|---|---|---|
| `requires` | G, agent_profile, skill → G or R; asset → asset; mission_type → action | 468 |
| `suggests` | G, agent_profile, skill → G, R or agent_profile | 575 |
| `scope` | mission_type or action → G or agent_profile | 223 |
| `refines` | G → G | 19 |
| `instantiates` | action → template | 11 |
| `specializes_from` | agent_profile → agent_profile | 7 |
| `enhances` | step_enhancement → action; X → X for the other activatable kinds except mission_type | 5 |
| `rejects` | G → anti_pattern | 13 |
| tension (`in_tension_with`) and `reconciles_tension` | G → G | 6 and 6 |
| `vocabulary` | any authored kind → glossary (`glossary_pack` until #106) | 1 |
| `applies` | action → directive or tactic | 0 |
| `overrides` | X → X, same kind, X activatable and not step_enhancement | 0 (1 skill → skill in charter) |
| `implemented_by` | guard → asset, derived only, never authored | n/a |

What this table would refuse:

- **0 of the 1,331 open-packs edges.**
- **3 of the 48 example-pack edges:**
  - the 2 `impacts` edges;
  - one `suggests styleguide → anti_pattern`, which should be a `rejects` edge.
- Error fixtures that would gain a second fault, to be rewritten so that each tests one fault:
  - 6 `impacts` edges in load fixtures;
  - 4 `impacts` edges in planted fixtures;
  - one `scope tactic → tactic`.

**#447.** The `overrides` row requires the source to be the same, activatable kind as the target. That is #447's
option 1, refusal at registration. The census finds **0** `overrides` edges from a non-activatable kind in either
corpus.

Slice C already builds option 1 as FR-014 (R8), so F does not deliver #447. F generalizes C's rule into the endpoint
table, and adds the "same kind" half.

**Retired relations still in the enums.**

- `impacts` and `replaces` are both still carried:
  - `RelationType.IMPACTS`'s javadoc says "the signed `impacts` relation that ADR-0028 retires";
  - `replaces` is "kept for older hand-authored fragments".
- `delegates_to` has no authored edge, and ADR-0064 decision 11 keeps delegations as data.
- With the endpoint table, each relation needs a row or goes.
- The canonical-model rule says to replace the old shape outright. Retiring all three here **widens #515**, so it is
  flagged, not done (Q6).

**Two relation enums.** `kernel.domain.Relation` (15 values) and `RelationType` (16, adds `IMPLEMENTED_BY`) both
exist. The typed constructors belong on one; the plan picks `RelationType`. Merging the two enums is plan work, not a
ruling.

**Where the rule lives.** Recommended placement, to be confirmed with the #549 session (R9):

- The endpoint table and the typed constructors go in the kernel domain, `RelationType` and `Relationship`.
- The read-side refusal goes where graph documents are bound:
  - today that is `PackPolicy.endpoints` (validate mode, `RelationshipShard`);
  - and `DomainRepositoryService.typedEdge`, on the registration load, which today drops an edge that does not bind
    without refusing it;
  - after #554, a rule in the graph ruleset (ADR-0075 decision 3).
- The write-side refusal is in `RelationshipRegistrationService`, following `refuseStepEnhancementEdge`.

**Refusal shape (revised 2026-11-13, after reading slice C).**

- Slice C already refuses one row of this table. Its FR-014 builds `OverrideSourceRule`
  (`packs.domain`) for #447.
- On write, its refusal is `422 validation_failed` with a violation `overrides_source_not_activatable` at `/from`.
- On a composing read of a stored pack, it is `500 pack_unreadable`.
- The checks run in `RelationshipRegistrationService` and `PackPolicy` on branch
  `slice-c/effective-step-context`, at `b336e590`.

Proposed for F:

- **Follow C's pattern** rather than mint a new top-level refusal code: one violation code,
  `relationship_endpoint_not_allowed`, inside `422 validation_failed`, and `pack_unreadable` on read.
- **Replace `OverrideSourceRule` with the general endpoint rule.** Its `overrides` row is one case of the table, so a
  separate rule would be a second source for one fact.
- **Ruling needed:** whether C's violation code `overrides_source_not_activatable` folds into the general code, or
  stays as the `overrides` row's own code (Q7, revised).
- The reserved `unresolved_edge_endpoint` means a missing node, not a disallowed kind, so it is not reused.

## R7. Glossary joins the taxonomy later (#106)

`glossary` replaces `glossary_pack` outright in #106's mission, run by another session.

- Slice F's kind table is generated from `ArtifactKind`, so it follows that change with no extra work.
- The endpoint table's `vocabulary` row names the target as "the glossary kind".
- Whichever lands second rebases.

Not blocking.

## R8. Collisions with slice C (#467)

- **No slice C mission branch is pushed**, and there is no slice C directory in `kitty-specs/`. Its state is asked of the
  orchestrator.
- **Slice C** is mission `effective-step-and-action-context-01M774HB`, on `origin/slice-c/effective-step-context`
  at `b336e590` (orchestrator, 2026-11-13). A rebased branch, `slice-c/effective-step-context-rebased`, is being
  prepared.
- **Slice C delivers #447 (its FR-014).** It adds `OverrideSourceRule` and refusals in `RelationshipRegistrationService`,
  `PackPolicy` and the activations read. So #447 stays with C. F rebases onto it and folds C's rule into the endpoint
  table (R6, Q7 revised).
- **Files both slices change.** C's diff against its merge-base touches all of these, which F also changes:
  - `PackPolicy`, `RelationshipRegistrationService`, `DomainRepositoryService` and `ImpactRelations`;
  - `EffectiveReadService`, `ClosureViewSession`, `MissionBodies`, `EffectiveMissionTypesController` and
    `EffectiveMissionTypeReadService`;
  - `RefusalCode`, `CharterRefusalCode.yaml`, `ReservedRefusalCodesTest` and `packs_packId_relationships.yaml`.

  This confirms the ruled order: F implements only on top of C.
- **Effective step context (#438, #450):** `ActionContextWalk`, `EffectiveReadService` and `MissionBodies`.
  - `EffectiveReadService:702, 728-749` (`stepContractWinner`) and `MissionBodies:168-216` (delegation candidates)
    are step-contract code that R3 deletes.
  - If slice C edits them, F rebases onto C and deletes what C left (the ruled order).
- **`RelationshipRegistrationService`, `ImpactRelations` and the relationships contract** (`packs_packId_relationships.yaml`):
  F adds a refusal there. Slice C's #97 (R3, graph queries) reads `ImpactRelations` but is not known to change its
  write path. Confirmed at rebase.

## R9. Collisions with epic #549 (#554, #556)

- **#554** turns `PackPolicy`'s graph binding into a graph ruleset. F's read-side refusal is one rule in it.
  - If #554 lands first, F writes the rule in its vocabulary.
  - If F lands first, F puts the check in `PackPolicy.endpoints` and in the load path, and #554 carries it over.
  - Either way, the wire code is unchanged (ADR-0075 decision 4).
- **#556** moves `ImpactRelations` behind `ImpactCheck` and collects every fault. The endpoint rule is not an impact
  check; it does not move there.
- **ADR-0075 decision 2 names `MissionStepContractIdentifier`**, which R3 deletes. F amends that line.
- **PR #561 (#553)** touches none of F's files.
- Sequencing goes to the orchestrator, for the #549 session (`session_019C2Tk51M6V1VjNNKNiuSwX`). The plan is
  written to work in either order.

## R10. Breaking changes and the CHANGELOG

These go under one `### Breaking` entry in a 0.x minor (ADR-0022):

- retiring `mission_step_contract`: the operation, the schemas, the enum values, the refusal code, the `step-contract`
  link and the preset and activation members;
- the new endpoint refusal, since an edge that registered before is now refused;
- dropping `template_set` from `charter.schema.json`, if Q3 rules so.

The charter's model replaces the old shape outright: no deprecation window and no legacy reader (charter.md). Fixtures,
example packs and contract examples move with the model.

## R11. Similar-surfaces squad

Dispositions are in `research/squad-similar-surfaces.md`. In short, every part of the slice has a surface to reuse.

- **The kind table already exists as data.** `contracts/packs/file-kinds.yaml` lists node kinds, plurals and
  relations. `VocabularyEqualityTest` and `WireTokenParityTest` keep it equal to the domain. The R18 body is built
  from the same enums, and its parity case joins those tests.
- **`RelationType`** already takes per-relation constructor flags, so the endpoint rule becomes one more.
- **The `retired:` file kinds and `RetiredMissionFieldNamesTest`** carry the kind's retirement.
- **One dated `### Breaking` fragment** goes under `contracts/charter/changes/`. `CHANGELOG.md` is never edited
  directly.
- **The planted and load-fixture patterns** hold the invalid packs.

## R12. Tracked-issues squad

Dispositions are in `research/squad-tracked-issues.md`. The findings:

- **Closes:** #112, #514, #520, #515 and #533. #447 is delivered by slice C (R8).
- **#350**, refusing a procedure edge to `action:`, is one row of the endpoint table. It is proposed as in scope only
  if the operator agrees (Q8).
- **Refs:** #49, #154, #337, #355, #417, #433 and #559.
- **Sequenced:** #339 (moving the gate's content), #547 (checked by hand at landing), #549 and #106.
- **#519** looks obsoleted by ADR-0064. It is reported to the orchestrator and not acted on.
- **No new issue is filed** (issue-tracker.md, Rule 1).

## R13. Open rulings for the operator

| # | Question | Recommendation |
|---|---|---|
| Q1 | R18 shape: `GET /offering/kinds` with `kinds` and `relations`, no core org facts, not deferred to merge | As proposed (R2) |
| Q2 | Retire `mission_step_contract` by moving delegations to `scope` edges, the gate to the lifecycle guard and substep text into prompts, losing substep structure until #513 | Yes (R3) |
| Q3 | `charter.schema.json`'s `template_set` (#520 "template names only the kind") | Drop it (R5) |
| Q4 | `mission_type → action`: derive it from `steps` and refuse an authored copy, or keep it authored | Derive (R5) |
| Q5 | The endpoint table (R6) | As drafted |
| Q6 | Retire `impacts`, `replaces` and `delegates_to` in this slice (widens #515), or give them endpoint rows | Retire; flagged as a widening |
| Q7 | Revised: slice C delivers #447 (FR-014, `overrides_source_not_activatable`). F folds that rule into the endpoint table. Should its violation code merge into `relationship_endpoint_not_allowed`? | Merge: one rule, one code |
| Q8 | Include #350 (procedure edge to `action:`), a row of the endpoint table | Include |


## R14. Operator rulings, 2026-11-13 (interview)

The operator ruled directly in an interview. These rulings supersede the recommendations in R2 to R6 and in the R13
table where they differ.

| # | Ruling | Decision Moment |
|---|---|---|
| Q1 | As recommended: `GET /offering/kinds`, with `kinds` and `relations`, no core org facts, not deferred. | `DM-01M7B1YE4J84K0T4ZA27M48QKG` |
| Q2 | **Fold the substeps into the step** (not R3's recommendation). The substeps are a flat, ordered list, one level deep. All fields are kept: `id`, `description`, `command`, `inputs`, `delegates_to` and `guidance`. A delegation is data, and it also needs a `scope` edge for each candidate. The kind is then retired. #513 stays open, narrowed to recursion and `DECOMPOSES_INTO`. | `DM-01M7B1YH1GF15RD0G4ZPSA8KW9` |
| Q3 | Drop `template_set` from `charter.schema.json`. | `DM-01M7B1YKY84DQHZFDXEWNJGB5E` |
| Q4 | **Keep the edge authored, and make it complete** (not R5's recommendation). Every step, on-demand steps included, has a `requires` edge from its type. A pack that misses one is refused at load. | `DM-01M7B1YQR5ZKQPDW6DX64KPNRN` |
| Q5 | The endpoint table, as drafted. | `DM-01M7B1YTXP6QR1NQB784H4VX3W` |
| Q6 | Retire `impacts`, `replaces` and `delegates_to`. `replaces` was sometimes used to mean `overrides`, so a `replaces` edge migrates to `overrides`. | `DM-01M7B1YXJHQCBSYJ145NJC77V5` |
| Q7 | Slice C delivers #447. F folds `OverrideSourceRule` into the endpoint rule and **keeps C's code** `overrides_source_not_activatable` for that fault. | `DM-01M7B1Z03VF9A36FBV65431HR3` |
| Q8 | Include #350. | `DM-01M7B1Z30AV54T9WM2WR61EK30` |
| P1 | **One new ADR** holds every slice F decision. The touched ADRs get short notes. | `DM-01M7B3Q8R1C6J89MM3YWSMF8XZ` |
| P2 | Kind marker types, `NodeRef<K>`, per-relation factories and one checked bridge. | `DM-01M7B3QBD0W0KTBHPHX55RB33K` |
| P3 | One PR with ordered work packages. | `DM-01M7B3QE377FQXN0R02WNC829W` |

Checked after the ruling:

- All 28 open-packs step contracts name a step of their mission type, so each one folds into a step.
- No `replaces` edge exists in any corpus.

The census script is `research/edge-census.py`, with its output in `research/edge-census.csv`.

## R15. Post-plan squad, 2026-11-13

Three lenses reviewed the plan and found 4 blockers and 14 majors, all folded. The dispositions are in
`research/squad-post-plan.md`. Two corrections to this note:

- **R3's delegation count.** It is **80** missing `scope` edges from the step's action, not 72: 72 counted
  mission-type edges as covering a delegation.
- **R8's slice C reference.** Slice C is on `main` (PR #566, `cdc62e1cf`), and this branch merged it at `ffb6b4929`.
