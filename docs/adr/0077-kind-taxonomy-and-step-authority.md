---
title: "ADR-0077: The kind taxonomy is served, and the mission-type step is the one authoring surface"
description: "Slice F: the kind taxonomy is served; substeps fold into the step and the step contract retires; each relation's endpoint kinds are typed and refused; four shapes retire."
doc_status: active
updated: 2026-11-14T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-13'
type: ADR
kind: ADR
related:
  - adr/0022-contract-versions-start-at-0-x
  - adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship
  - adr/0044-mission-types-consolidated-into-one-sanitized-shape
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0054-guards-are-enforced-checks-implemented-by-executable-assets
  - adr/0064-graph-documents-are-the-only-source-of-relationships
  - adr/0074-the-remaining-cutover-reads-and-their-paths
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you change a relation, a kind, the endpoint table, a mission-type step or the kind taxonomy read.
  - profile: pack-author
    guidance_text: Read this for where a step's substeps are authored, which edges each relation may draw, and which kinds and relations are gone.
  - profile: core-maintainer
    guidance_text: Read this for the kind taxonomy read that replaces core's hand-kept kind lists, and for what the generated client loses with the step contract.
---

# ADR-0077: The kind taxonomy is served, and the mission-type step is the one authoring surface

## Status

**Accepted**, 2026-11-13. Decider: Stijn Dejongh (operator).

- **The rulings.** The operator ruled slice F ([#537](https://github.com/spec-kitty/spec-kitty-charter/issues/537)) in
  an interview on 2026-11-13: eight specify rulings and three plan rulings, each recorded as a Decision Moment of the
  mission `slice-f-kind-taxonomy-01M7B19K` (research R14). Three more followed on 2026-11-14, during the build
  and at landing (decisions 2 and 4), and four on 2026-11-15, at the landing review (decisions 4 to 7): eighteen in
  all. The rulings are marked *(operator)* below.
- **One record.** Plan ruling `DM-01M7B3Q8R1C6J89MM3YWSMF8XZ` puts every slice F decision in this record. The
  records it touches carry a short note that points here.
- **The squad.** A three-lens post-plan squad (domain, contract, migration) found 4 blockers and 14 majors in how the
  plan carried the rulings out; all were folded before the build. It contested no ruling.

It closes [#112](https://github.com/spec-kitty/spec-kitty-charter/issues/112) (R18),
[#514](https://github.com/spec-kitty/spec-kitty-charter/issues/514),
[#515](https://github.com/spec-kitty/spec-kitty-charter/issues/515),
[#520](https://github.com/spec-kitty/spec-kitty-charter/issues/520),
[#533](https://github.com/spec-kitty/spec-kitty-charter/issues/533) and
[#350](https://github.com/spec-kitty/spec-kitty-charter/issues/350), and it narrows
[#513](https://github.com/spec-kitty/spec-kitty-charter/issues/513).

It **amends**, each with a dated note where it stands:

- [ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/) decisions 2 and 3: the step contract is
  retired and every step is linked from its type (decision 2); a step authors `substeps`, and `template_set` is refused
  by name (decision 3);
- [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) decisions 4 and 5: the
  `step_contracts/` directory and the `mission_step_contract` kind go;
- [ADR-0054](/adr/0054-guards-are-enforced-checks-implemented-by-executable-assets/) decision 12: the migration is
  done, and `mission-step-contract.schema.json` is gone with it;
- [ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) decisions 11 and 13: the endpoint table,
  and a delegation kept as substep data;
- [ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decision 12: the step-contract gap (#533) is
  closed as moot;
- [ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/) decision 2:
  `MissionStepContractIdentifier` leaves its list.

The records that only name the retired kind carry an inline note: ADR-0024, 0036, 0037, 0042, 0048, 0049, 0053, 0071
and 0073. So do the records that name a retired relation: ADR-0028, 0045 and 0059.

## Context

- **A client re-derives the model's kinds.** Core keeps its own lists of the kinds and relations, and of which kind a
  reference may name. The service had no read for them: R18 was one of the last undecided cutover reads of epic
  [#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94).
- **A step was authored in two places.** A mission type's steps lived in its mission-type file. Each step's
  substeps, their commands, inputs, delegations and guidance, and one gate lived in a separate `mission_step_contract`
  artifact that named the step. The two were kept in step by hand.
- **Any relation joined any two kinds.** Besides the step-enhancement rules and slice C's `overrides` source rule
  ([#447](https://github.com/spec-kitty/spec-kitty-charter/issues/447)), nothing checked that an edge's kinds made sense:
  `procedure -requires-> action` (#350) or an `overrides` edge between two kinds loaded.
  [ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/) decision 13 ruled on 2026-11-12 that
  each relation names its endpoint kinds, typed in the domain and refused at read and write, and left the table to
  this slice.
- **Three relations had no place.** `impacts` was retired as a relation by
  [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/) but still carried, with its strength.
  `replaces` was kept "for older hand-authored fragments". `delegates_to` had no authored edge.
- **The mission-type chain was incomplete.** Open-packs authored 21 `mission_type -requires-> action` edges for 47
  steps, and the charter schema still declared core's `template_set`.

The census behind the table counted 1,331 authored edges in open-packs and 48 in charter's example packs. The table
refuses none of the open-packs edges, and 3 of the example-pack edges before their migration (research R6).

## Decision

1. **The kind taxonomy is served as data, at `GET /offering/kinds` (`getKindTaxonomy`)** *(operator,
   `DM-01M7B1YE4J84K0T4ZA27M48QKG`)*.
   - It answers two lists, in the model's order:
     - `kinds`: every node kind, with its `plural` (absent when it has none), its `artifactKind` (absent for
       `action`), whether it is an `artifact`, whether an activation selects it (`activatable`), whether a graph
       document may declare it (`declarableInGraphs`) and whether an authored edge may target it (`edgeTarget`), and a
       templated `artifacts` link, built from its `artifactKind`, for each kind the pack artifact list serves;
     - `relations`: every relation, with `directed`, `derived`, `heldAs` (`relationship`, or `tension` for
       `in_tension_with`) and its allowed `{source, target}` kind pairs.
   - It holds none of core's org facts (`effective_when_absent`, `selection_overlayable`, `org_requirable`). It is
     built and served now, not deferred to the minor that brings merge.
   - The body is built from the kernel's `NodeKind`, `ArtifactKind`, `RelationType` and `Tension`, never from a
     hand-kept list. A parity test keeps it equal to the enums and to `file-kinds.yaml`, with skewed positive
     controls.
   - Its `ETag` is the sha256 of the served body, in `ContractETag`'s pattern, and it answers `If-None-Match` with
     `304`. It carries the `Charter-*` headers and no source fingerprint, so it is an exception of the two
     fingerprint lint rules. The root and the offering link it as `kinds`.
2. **A step's substeps are authored in the step** *(operator, `DM-01M7B1YH1GF15RD0G4ZPSA8KW9`)*.
   - A step and an on-demand step may carry `substeps`: a flat, ordered list, one level deep. Each substep keeps every
     field a step contract's step held: `id`, `description`, `command`, `inputs` (`{flag, source, optional}`),
     `delegates_to {kind, candidates}` and `guidance`.
   - The grammar: a substep `id` is a letter-first kebab slug, unique within its step (`duplicate_step_id`
     otherwise). An input's `optional` is written only as `true`. `delegates_to.kind` is a `scope` target of an
     action and a guidance kind, never `agent_profile` *(operator, 2026-11-14, `DM-01M7DTN8JWR4G1P35NXGCMS65M`)*:
     a mission type names roles, never profiles, so this keeps
     [ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/) decision 1. Each candidate is written `[<pack>::]<id>`, once, with the kind given
     once by `kind`.
   - **A delegation is data, and it needs its edge.** It stays the runtime handoff of ADR-0064 decision 11. Each
     candidate must resolve to an artifact of `kind` (`substep_delegation_unresolved`), and the step's
     `action:<type>/<step>` node must have a `scope` edge to it (`substep_delegation_unscoped`). A bare candidate is
     judged at load; a candidate written with another pack's name is judged when the packs compose and at
     registration. Removing the last `scope` edge a candidate needs is refused.
   - The step reads answer the substeps in authored order. Each candidate links its read when the scope holds it:
     `artifact` in a pack, `effective-artifact` under an activation. The delegation components are renamed
     `MissionSubstepDelegation` and `MissionSubstepDelegationCandidate`.
   - #513 stays open, narrowed to recursive substeps and `DECOMPOSES_INTO`.
3. **`mission_step_contract` is retired outright** *(operator, `DM-01M7B1YH1GF15RD0G4ZPSA8KW9`; #514)*.
   - Gone: its kind and node values, the operation `getEffectiveMissionStepContract` and its switch-gate key, its
     schemas, parameter, response and example, the refusal code `mission_step_contract_not_found`, the `step-contract`
     link and its `step` link relation, the preset and activation members, the pack schema and file kinds, and the
     domain types.
   - A `*.step-contract.yaml` file or a `mission_step_contract.graph.yaml` fragment is refused by name as a retired
     file kind (`file-kinds.yaml`, `retired:`). The retired routes answer as unknown: `404 kind_not_found` on the item
     routes, `400` on `?kind=`.
   - **The one gate becomes a lifecycle guard** (ADR-0054 decision 12): the `review` step contract's gate is the guard
     `spec-kitty-pre-review` on the work-package lifecycle's `in_progress -> for_review` transition, with
     `on_indeterminate: pass` in place of `fail_open`.
   - No step-contract name is left in the main sources or the contract outside retired-name lists (NFR-003), and a
     scan test keeps it so.
4. **Every step is linked from its type, by an authored edge** *(operator, `DM-01M7B1YQR5ZKQPDW6DX64KPNRN`)*.
   - The `mission_type:<type> -requires-> action:<type>/<step>` edge stays authored and must be complete: one per step,
     on-demand steps included. A pack that misses one, or holds such an edge to an action that is no step of the type,
     is refused (`mission_step_unlinked`): at load per pack, and again at compose on the winning type.
   - A relationship write keeps it so: putting a stray edge, or deleting the last edge that links a step, is refused.
   - **A mission type's removal drops its own step links and its steps' scope edges** *(operator, 2026-11-14,
     `DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ` and `DM-01M7DTNBMQ6Y88RKJWD3ZFH6G6`)*. `deletePackArtifact` of a mission type
     removes the type's own `requires` edges to its steps, and the `scope` edges whose source is one of its own step
     actions (`action:<type>/<step>`, bare or written with the pack's own name), from the graph documents that hold
     them, so a type whose substeps delegate can be removed. It is the one case where a removal rewrites an
     author-written document; any other authored reference to the type or its actions still answers `409`. The same
     edges go from `registered.graph.yaml` too *(operator, 2026-11-15, `DM-01M7F1ARDGCHPSB62QNY0EZW7Y`)*: one rule
     for authored and registered edges, or a delegating type whose scope edges were registered would still answer
     `409`.
5. **Each relation's endpoint kinds are a table in the kernel, typed and refused** *(operator,
   `DM-01M7B1YTXP6QR1NQB784H4VX3W`, `DM-01M7B1Z30AV54T9WM2WR61EK30`, `DM-01M7B1Z03VF9A36FBV65431HR3` and
   `DM-01M7B3QBD0W0KTBHPHX55RB33K`)*. The table is research R6's, as drafted. G is the six guidance kinds: directive,
   tactic, styleguide, toolguide, paradigm and procedure.

   | Relation | Source → target |
   |---|---|
   | `requires` | G, agent_profile or skill → G, asset or template; asset → asset; mission_type → action |
   | `suggests` | G, agent_profile or skill → G, asset, template or agent_profile |
   | `applies` | action → directive or tactic |
   | `scope` | mission_type or action → G or agent_profile |
   | `vocabulary` | any kind authored in graphs → the glossary kind |
   | `instantiates` | action → template |
   | `refines` | G → G |
   | `specializes_from` | agent_profile → agent_profile |
   | `enhances` | step_enhancement → action; X → X for every activatable X except mission_type and step_enhancement |
   | `overrides` | X → X, where X is activatable and not step_enhancement |
   | `reconciles_tension` | G → G |
   | `rejects` | G → anti_pattern |
   | `implemented_by` | guard → asset (derived, never authored) |
   | tension (`in_tension_with`) | G ↔ G, held beside the relations as a `Tension` (ADR-0028) |

   - **One source.** `RelationType.endpoints()` answers from one table (`EndpointTable`). An agreement test reflects
     over the typed factories and checks that their products equal each row, with a skewed row as the positive
     control.
   - **Typed by kind markers.** Each kind has a sealed marker type, grouped by sealed interfaces such as `Guidance`
     and `Advisor`. A `KindRef<K>` is built only through per-kind leaf factories, so no reference to a group exists.
     `Relationship` has one static factory per rectangle of the table (`requires`, `requiresAsset`, `requiresStep`,
     `enhancesStep`, ...), so a combination the table does not allow does not compile through them. Same-kind
     relations hold by invariance. The untyped `Relationship.of`, which takes URNs, is not checked at compile time:
     it binds through `EndpointRule` and refuses a disallowed pair at run time.
   - **One checked bridge.** Code that holds only URNs binds through `EndpointRule.bind`, or `bindTension` for a
     tension, which looks the pair up in the table and answers the relationship or a fault naming the relation and
     both kinds. The raw constructor is package-private, and the ArchUnit rule `RELATIONSHIP_IS_BOUND` lets only
     the `Relationship` and `EndpointRule` classes call it.
   - **The fault order is fixed**, so an edge that breaks two rules reports one: the step-enhancement rules first
     (`prompt_fragment_invalid`), then slice C's `overrides` source rule, which keeps its code
     `overrides_source_not_activatable`, then the table (`relationship_endpoint_not_allowed`).
   - **Slice C's rule is folded into the bridge.** `EndpointRule` owns the source rule's one predicate and its one code,
     and `bind` checks it before the table. `EndpointRule.overridesSourceRefusal` applies the same rule to an edge as
     written, on its source alone, so a reader reports it whatever the target is. Slice C's `OverrideSourceRule` is
     deleted. The load and validate mode report both faults through `RelationshipEndpointRule`, which takes each one
     from the bridge, and the relationship write answers the bridge's fault: `/from` for the source rule, `/type` for
     the table. The pointers, the wire codes and the reason texts are unchanged.
   - **Refused at read and write.** A registration answers `422 validation_failed` with one violation per edge at its
     graph document; `putPackRelationship` answers it with the violation at `/type`; a composing read answers
     `500 pack_unreadable`, naming the document and then the code `relationship_endpoint_not_allowed` before the
     reason *(operator, 2026-11-15, `DM-01M7F1AXQC17ARGCR0AV3DGPEX`)*; validate mode reports a blocking finding. Until
     [#554](https://github.com/spec-kitty/spec-kitty-charter/issues/554) lands, the read check sits where graph
     documents are bound, `PackPolicy` and the load path; whichever lands second moves it, and the wire codes do not
     change (ADR-0075 decision 4).
6. **`impacts`, `replaces` and `delegates_to` are retired** *(operator, `DM-01M7B1YXJHQCBSYJ145NJC77V5`)*.
   - They leave `RelationType`, the contract's `Relation` and the pack schemas. An edge that uses one is refused as an
     unknown relation, and an edge's `impacts` strength is an unknown member. `putPackRelationship` with one as its
     `type`, as with any type that names no relation, answers `422 validation_failed` with one violation
     `relationship_type_unknown` at `/type`, as other `422` refusals carry one *(operator, 2026-11-15,
     `DM-01M7F1B2F1Z82D4SM344JSJB4H`)*.
   - `replaces` was sometimes written to mean `overrides`. A `replaces` edge is rewritten as `overrides`, a content
     migration done in open-packs, not by the service. No corpus held one on 2026-11-13.
   - `kernel.domain.Relation`, which had no caller in the main code, is deleted; `RelationType` is the one relation
     enum.
7. **`template_set` is dropped** *(operator, `DM-01M7B1YKY84DQHZFDXEWNJGB5E`)*. It leaves `charter.schema.json`
   outright, and a project charter that carries it is refused as an unknown field. A mission type that authors
   `template_set` or `action_sequence` is refused as an unknown field, by its name (#520). "Template" names only the
   artifact kind. A repository's `charter.yaml` is that repository's activation set, so the refusal is proven at
   contract level through the activation seam *(operator, 2026-11-15, `DM-01M7F1B742YTQHEW5QYZJ8GTQD`)*: a
   `createActivation` or `replaceActivation` body that carries `template_set` answers `422 activation_invalid` with
   one `unknown_member` at the key's pointer.
8. **Records and delivery** *(operator, `DM-01M7B3Q8R1C6J89MM3YWSMF8XZ`, `DM-01M7B3QE377FQXN0R02WNC829W`)*. One pull
   request from `slice-f-537-kind-taxonomy` into `main`, with ordered work packages: content first, then each rule.
   The contract break ships in a 0.x minor (ADR-0022), recorded in `### Breaking` change fragments.

### Reviewed calls of the build

These were made in the work packages and approved in their reviews; none changes a ruling.

- **The taxonomy carries its own enums** (WP09). `KindRow.kind` and `RelationRow.relation` reference
  `TaxonomyKind` and `TaxonomyRelation`, not `NodeKind` and `Relation` as plan IC-04 drafted. The contract's
  `NodeKind` leaves out `guard` and `lifecycle`, and `Relation` leaves out `implemented_by` and `in_tension_with`,
  because no graph read or relationship write names them; the taxonomy names every kind and relation. A parity test
  keeps the two enums equal to the domain's.
- **The read lives in `OfferingController`** (WP09), not a new `KindTaxonomyController`: the generated `OfferingApi`
  is one interface per tag.
- **The `step` link relation is dropped** (WP08). Nothing emits it once the step contract is gone, so it leaves the
  lint ruleset and the hypermedia relation table, which `RelationTableParityTest` keeps equal.
- **A repeated substep id is `duplicate_step_id`** in validate mode and at load alike, at the mission-type file
  (WP07, WP08).
- **A retired file rule beats a files rule that also matches**, unless an exclusion matches too (WP08), so a retired
  `mission_step_contract.graph.yaml` is refused by name and not read as a graph shard.

## Consequences

### Positive

- A client reads every kind and relation, with its endpoints, from one resource, and drops its hand-kept lists.
- A step is authored once. The step reads carry its substeps, and the delegations link their reads.
- Each relation means one thing: a disallowed edge does not compile through the typed factories, is refused at run
  time by `EndpointRule` when built from URNs, and is refused at read and write with a named code.
- The mission-type chain is complete, so every step's action is reachable from its type.

### Negative

- **Breaking.** Every removal above, and every new refusal, breaks a 0.x client and pack content that relied on it
  (ADR-0022). The change fragments list each one and what core's seam has to change.
- **The packs schemas break too.** `mission-step-contract.schema.json` is removed, and `mission-type`, `graph-shard`,
  `org-fragment`, `pack-manifest`, `policy`, `preset` and `charter` change. A consumer of `contracts/packs`
  ([#559](https://github.com/spec-kitty/spec-kitty-charter/issues/559)) meets the same break.
- **open-packs breaks until it follows.** It must fold its 28 step contracts into their steps, remove the files and
  the 2 graph fragments, add 26 `requires` edges with 23 action nodes and 79 `scope` edges for 80 candidate uses,
  author foundation's work-package lifecycle and its `spec-kitty-pre-review` guard, and update its validator. The drafts are in the
  mission's research directory. Open-packs proves its content against a pinned charter release (ADR-0070), so `main`
  is not blocked.

### Risks

- **The effective step context widens.** `ActionContextWalk` walks `scope` edges, so each new `scope` edge from a
  step's action puts its delegation candidate in force for the step's effective context. This is the ruled
  consequence of decision 2 (squad finding D-M10), not a side effect.
- **Core aligns later.** Core keeps its step contracts and kind lists until it is incorporated; it aligns through the
  generated client, with no core-first ordering (charter.md). The project's own `.kittify/charter/charter.yaml` is
  generated by core's CLI and still lists a `template_set` library entry until core aligns.
- **Sibling work in flight.** #554 moves the read check into its graph ruleset (decision 5). #106 renames the artifact
  kind `glossary_pack` to `glossary`, whose node kind is `glossary_context`, and adds the node kind `term`; the
  endpoint table is keyed by `NodeKind`, not `ArtifactKind`, so its `vocabulary` targets become `glossary_context` and
  `term`. #552 drops `MissionStepContractIdentifier` from its identifier list. Whichever lands second rebases.

## Alternatives considered

### Retire the step contract and lose the substep structure until #513

Research R3 proposed moving the delegations to `scope` edges, the gate to the lifecycle guard and the substep text into
the prompts. Not chosen *(operator)*: the substeps fold into the step with every field kept.

### Derive the type-to-step edge from `steps` and refuse an authored copy

Research R5 proposed it, as `implemented_by` is derived. Not chosen *(operator)*: the edge stays authored and must be
complete.

### Give the retired relations endpoint rows

Not chosen *(operator)*: none has a place in the model, and the canonical-model rule replaces an old shape outright.

### Fold `overrides_source_not_activatable` into the general code

Research R13 recommended one rule and one code. Not chosen *(operator)*: slice C delivered #447 with its own code, which
the fault keeps.

### Per-relation record types, or one factory per relation

Not chosen *(operator, plan)*: `requires` and `enhances` are unions of rectangles, so one factory per relation lets a
disallowed pair compile, and overloads erase to one signature. Kind markers with one factory per rectangle do not.

## References

- Mission `slice-f-kind-taxonomy-01M7B19K`: its spec, plan, data model, research note (R2, R3, R6, R14, R15) and
  post-plan squad.
- [#537](https://github.com/spec-kitty/spec-kitty-charter/issues/537) (slice F), epic
  [#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94), and slice C (PR
  [#566](https://github.com/spec-kitty/spec-kitty-charter/pull/566))
- [`contracts/packs/file-kinds.yaml`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/packs/file-kinds.yaml)
  (the retired file kinds)
