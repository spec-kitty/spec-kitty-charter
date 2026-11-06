# Research: Graph documents are the sole source of edges

Grounding squad, 2026-11-05: architect, service-code, contract and pack-content lenses, run as
parallel read-only subagents. The open-packs figures are taken at the pinned `openPacksRef`
`dad323f`. File paths are relative to `service/`, except where they start with `contracts/`.

## R1. Edge-minting fields today

The method the issue calls `edges()` is `CharterArtifact.relationships()`, at
`kernel/.../kernel/domain/CharterArtifact.java:64`. Its javadoc, at :55-62, describes the interim
mapping. The helper is `packs/.../packs/domain/ArtifactEdges.java` (`build` at :55, `effectiveOrigin`
at :93). A typed `{type,id,when?,reason?}` reference mints `SUGGESTS`. A bare id mints `REQUIRES`.

| Kind | Field (YAML / Java) | Relation minted | Domain | DSO / Yaml / Binding | Contract (read / registration) | Pack schema | Decision |
|---|---|---|---|---|---|---|---|
| directive | `procedures` / `procedures` | `REQUIRES procedure:<value>` | Directive.java:51, :151-156 | DirectiveDso:30, DirectiveYaml:44/69, DirectiveBinding:46/65 | Directive.yaml:55 / DirectiveRegistration.yaml:56 | directive.schema.json:116 | **Keep as prose; stop minting** (D1) |
| directive | `references[]` / `DirectiveReference` | `SUGGESTS` (+when, reason) | Directive.java:55, :152 | DirectiveDso:34, DirectiveYaml:30-73, DirectiveBinding:50-69 | Directive.yaml:87 / DirectiveRegistration.yaml:88 | directive.schema.json:145, `$defs` :25-66 | **Drop** |
| paradigm | `directive_refs` / `directiveRefs` | `REQUIRES directive:<id>` | Paradigm.java:44, :172-177 | ParadigmDso:22, ParadigmYaml:40/58, ParadigmBinding:42/54 | Paradigm.yaml:38 / ParadigmRegistration.yaml:43 | paradigm.schema.json:67 | **Drop** |
| paradigm | `references[]` / `ParadigmReference` | `SUGGESTS` | Paradigm.java:45, :173 | ParadigmDso:23, ParadigmYaml:29-59, ParadigmBinding:43/55 | Paradigm.yaml:46 / ParadigmRegistration.yaml:51 | paradigm.schema.json:75, `$defs` :25-44 | **Drop** |
| tactic | top-level `references[]` / `TacticReference` | `SUGGESTS` (+when) | Tactic.java:41, :122-131 | TacticDso:31, TacticYaml:46/68/86, TacticBinding:52/75 | Tactic.yaml:61 / (no registration) | tactic.schema.json:148, `$defs` :25-53 | **Drop** |
| directive, paradigm | `relationships[]` (body) / `RelationshipWire` | as asserted | — | — | Directive.yaml:95, Paradigm.yaml:54 / DirectiveRegistration.yaml:96, ParadigmRegistration.yaml:59 | — | **Drop** |
| styleguide | `relationships[]` (always empty) | none | — | ArtifactDtos:208 | Styleguide.yaml:114 | — | **Drop** (D3) |
| guard | `implemented_by` | `IMPLEMENTED_BY` (derived, not registrable) | Guard.java:139-145 | GuardDso:28 | — | guard.schema.json:74 | **Keep: runtime data** |

Reference fields that mint no edge today (held "until #189 rules"):

| Kind | Field | Domain | Contract | Pack schema | Decision |
|---|---|---|---|---|---|
| procedure | `references[]` (typed) | Procedure.java:52 | (read only) | procedure.schema.json:166 | **Drop** (D2) |
| mission type, its steps | `references[]` (typed) | MissionType.java:55 | (read only) | mission-type.schema.json:106, :296 | **Drop** (D2) |
| tactic step | `references[]` (typed) | TacticStep.java:18 | TacticStep.yaml:27 | tactic.schema.json:82 | **Drop** (D2) |
| styleguide | `references[]` (bare citation strings) | Styleguide.java:51 | Styleguide.yaml:106 / StyleguideRegistration.yaml:98 | styleguide.schema.json:163 | **Drop** (D2, named by the ruling) |
| toolguide | `references[]` (bare citation strings) | Toolguide.java:59 | Toolguide.yaml:78 / ToolguideRegistration.yaml:122 | toolguide.schema.json:60 | **Drop** (D2) |

These look like references but are kept as data (FR-013; the operator is asked to confirm, Q4):

- agent-profile `directive-references` and its tactic, toolguide and styleguide references
  (AgentProfile.java:73-76), `collaboration`, and `handoff-*`;
- skill `enhances` and `overrides` (Skill.java:56-57);
- pack `depends_on`;
- preset and charter `activated_*` and `selected_*`;
- glossary `see_also`;
- mission-step-contract delegations;
- lifecycle guard entries;
- per-value `impacts`.

## R2. Where `relationships()` is consumed

- `ArtifactDtos.java:173, :208, :250`: the read-shape `relationships` arrays. Removed (D3).
- `GraphWalk.java:106`: only tests construct it. It moves onto graph-document edges, or is retired.
- `ArtifactReadService.java:382-386`: `Reference.relation` for the references read. This now comes
  from the pack's graph edges (D3).
- `CharterArtifact.writtenReferences()` default at :74, used by `PackPolicy.java:732` and
  `DomainRepositoryService.java:338`. Once the fields go, an artifact writes no references, so the
  references read is built from graph edges.

## R3. Registration write paths

- The directive and paradigm controllers parse `body.relationships`. `RelationshipMerge.ownBodyRelationships`
  then merges them into the registered shard (DirectiveRegistrationService:110-121, ParadigmRegistrationService:111-122).
  The read shape, meanwhile, derives its `relationships` from the fields. So today a GET never shows
  the edges a body registered: there are two copies, and they disagree.
- `putPackRelationship` (RelationshipController:49-57 → RelationshipRegistrationService:87-117) writes
  to `registered.graph.yaml` (CommitStorePackWriteStore:124). This becomes the only write path.

## R4. Round-trip gates

- `WholePackRoundTripTest` compares YAML meaning per file. Edge fields are compared as YAML keys, and
  graph shards by their canonical edge set.
- `WholePackOpenPacksRoundTripTest.meaning()` (:81-103) compares the per-kind DSOs and `graphs()`.
- Once the fields leave the DSOs, edges are compared through `graphs` only (FR-011).
- The pack schemas are closed (`additionalProperties: false`), so a pinned file that still carries a
  removed field is refused. Each such file goes on `packs/src/test/resources/whole-pack/known-failures.yaml`.
  Its issue key pattern is `WholePackExpectation.java:41`.

## R5. Contract

- Version `0.2.0-SNAPSHOT` (openapi.yaml:4). No release or tag exists, so no bump is needed. The change
  is recorded as a `### Breaking` fragment in `contracts/charter/changes/` (ADR-0022 D2).
- No request body is refused today for carrying an unknown member. The plan settles how FR-010 is
  enforced for bodies.
- Scenarios affected: directives.feature, paradigms.feature, styleguides.feature, artifacts.feature,
  composition.feature, and journeys/journey-06. relationships.feature is unchanged.

## R6. open-packs content at `dad323f`

| pack | kind.field | entries | files |
|---|---|---|---|
| built-in | directive.procedures (prose) | 179 | 38 |
| built-in | directive.references | 66 | 21 |
| built-in | paradigm.directive_refs | 35 | 12 |
| built-in | paradigm.references | 8 | 2 |
| built-in | tactic.references | 215 | 83 |
| internal | directive.procedures (prose) | 13 | 3 |
| internal | tactic.references | 3 | 2 |
| event-driven | directive.references | 11 | 6 |
| event-driven | paradigm.references | 13 | 1 |
| event-driven | tactic.references | 12 | 4 |
| qa | tactic.references | 9 | 8 |

These fields mint nothing but go too (D2):

- styleguide citations: 85 built-in, 7 internal, 1 cave-kitty;
- toolguide citations: 3;
- procedure typed references: 103 built-in, 28 internal, 10 qa;
- tactic step references: 21.

Field references checked against the graph documents:

| status | count |
|---|---|
| Already present with the same relation | 317 |
| Present with a different relation (the graph wins) | 38 |
| Missing from the graph | 17 |

The 17 missing references are 8 in event-driven and 9 in qa. Six of the event-driven ones point
opposite to an existing edge. 19 `when` texts exist only on the field. Directive `procedures` holds
192 prose sentences and is never a reference. The edge-by-edge detail for the cases that need action is in
`research/open-packs-migration.md`.

## Decisions

| ID | Question | Recommendation (built on) | State |
|---|---|---|---|
| D1 | directive `procedures` is prose | keep as text, mint nothing | deferred to operator (DM 01M6P9E60WTKQ737QKQRC61CT8) |
| D2 | which reference fields go | all typed refs + styleguide/toolguide citations | deferred (DM 01M6P9E9SMVNKD968XCPMXSYS8) |
| D3 | read shapes' relationships arrays | remove; references `relation` from graph docs | deferred (DM 01M6P9EDD6KC327Y1KK0Q2SM7K) |
| D4 | runtime/selection fields (FR-013) | keep as data | assumption A-4, asked as Q4 |
| D5 | ADR number | chosen at writing: 0064 (0059 to 0062 on main, 0063 taken by #398) | C-005 |

## R7. The open-packs migration is forward-compatible (part A)

The removed fields are optional in every pack schema; none is `required`. The migration was generated
on a scratch copy of open-packs at `dad323f`, committed there locally, and checked with `main`'s own
corpus gates (`-PopenPacksDir=<copy> -PopenPacksRef=<draft commit>`):

| Gate | Result on the migrated checkout |
|---|---|
| `:packs:wholePackRoundTrip` | green (8/8) |
| `:packs:openPacksRoundTrip` | 53/54; the one red is `OpenPacksComposeTest` "a typed reference put back to bare", which edits the very field the migration removes and is retargeted to the fragment edge by this mission |

The draft has three parts:

- Part A touches 213 files. It removes the fields from 206 artifact files, adds 28 graph edges,
  merges 31 `reason` texts and 1 `when` text into existing edges, and restamps the built-in
  `pack-manifest.yaml` with the ADR-0042 recipe.
- It does not add 9 references, because each pair already has an edge in the other direction, and
  ADR-0027 allows one structural edge per pair.
- 26 styleguide and toolguide citations that are not pack artifacts (URLs and docs paths) are
  dropped, and the draft lists each one.

Part B follows this mission. The org-fragment edge (`_OrgDRGEdge`) has no `when`, so 27 field `when`
texts in event-driven, qa and internal can move only once this mission adds `when` to it. Part B
also moves the open-packs validator's charter pin and removes its readers of the dropped fields.

## R8. Plan-point squad (architect, reviewer, reducer): dispositions

| # | Finding (lens) | Disposition | Where |
|---|---|---|---|
| 1 | `validation_failed` is 422, not 400; no member is named through the converter path (all three) | accepted | plan P4 |
| 2 | Global fail-on-unknown is too wide, collides with the deployable-config session, and is invisible to standalone MockMvc (reducer, reviewer) | changed: the check moves into the existing registration body advice | plan P4 |
| 3 | `RegistrationLists.of(ParadigmRegistration)` throws once its lists go, so the context fails to start (reducer, reviewer) | accepted | plan P4, IC-03 |
| 4 | The known-failure list cannot carry this change: every pack is affected, and six `verifyAll` corpus tests ignore the list (reviewer) | changed: part A of the migration lands first and this PR moves the pin; forward-compatibility verified | plan P5, R7 |
| 5 | The `#TBD-` precedent was added in b6dc899b and removed in a2581b61 (reviewer) | accepted: no `#TBD-` key is used | plan P5 |
| 6 | No port exists for graph edges, and the onion rule forbids `application` reading `infra` (architect) | accepted: `PackArtifactRepository.edges(pack)` | plan P3 |
| 7 | The references read needs defined scope and order, and `x-source` must change (architect, reducer) | accepted | plan P3 |
| 8 | ADR-0053 d22 (step enhancement `references`) and the ADR-0059 interaction are missing (architect) | accepted | plan IC-01 |
| 9 | GraphWalk would be a latent second walk model (architect, reducer) | deferred_with_rationale: retiring it amends ADR-0025's walk decision and touches the kernel; a follow-up issue is drafted, and the walk-slice tests pinning the mapping are deleted | plan P6 |
| 10 | Rename `relationships()` so authored overrides are not re-added (architect) | accepted: `derivedRelationships()` | plan P1 |
| 11 | Dead code: `ownBodyRelationships`, `mergeAll`, `fromWire(List)`, 3-arg `Change.artifact`, the reference types including `MissionReference`, item schemas, `$defs`, `referenceProblem` (reducer, architect) | accepted | plan P1, P2, IC-02, IC-03 |
| 12 | SC-002 and FR-010 can be faked by deleting tests (reviewer) | accepted: structural no-minting test; parameterized refusal per removed member and per pack schema | plan P1, P4, IC-02 |
| 13 | Docs and fixtures were not named (reducer) | accepted | plan IC-02, IC-05 |
| 14 | IC-03 and IC-04 overlapped (reducer) | accepted: concerns split by surface | plan concern map |
| 15 | US1 scenario 3 named a read that does not exist (reducer) | accepted | spec US1 |
| 16 | Agent-profile references and graph-authored profile edges are two homes (architect) | accepted: the ADR states why they are selection data | plan IC-01 |
