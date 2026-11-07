---
title: Ruleset scenario trace
description: Maps each counter-example of the layer-merge ruleset review and each acceptance scenario of its user stories to its decision, the scenario that pins it and its pull request.
doc_status: active
updated: 2026-11-07T00:00:00Z
type: API
kind: Reference
related:
  - adr/0059-switch-gate-cascade-edges-and-activation-rulings
  - architecture/composition-and-activation-rules
  - conformance/divergence-register
  - changelog/2026-11-07-a0-kind-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to find the scenario that pins a ruling, and to fill the rows of the pull request you build.
  - profile: governance-lead
    guidance_text: Read this to see which rulings of the ruleset review are built and pinned, and which are planned.
---

# Ruleset scenario trace

The layer-merge mission (`layer-merge-and-effective-reads-01M6R0J6`) rules every counter-example of its ruleset review
(`research/ruleset-review-2026-11-06.md`: A1 to A15 and B1 to B8) and builds the answers in four pull requests: A0, A, B
and C (the plan's PR slicing). Success criteria SC-002 and SC-005 ask that every acceptance scenario of its user
stories 1 to 6, and every counter-example, be pinned by a passing `@contract` scenario over fixture packs. This page is
that trace: one row per item, with the ruled answer, the decision, the scenario file and scenario that pin it, and the
pull request that builds it.

Scenario files are under `contracts/charter/scenarios/`. A row is **built** when its scenarios exist on the integrated
PR A0 branch and its work package was approved; it is **planned** when its scenario file is named and the scenario is written by the pull request
shown. The pull request A, B and C rows are filled by the documentation work package of that pull request (WP20, WP26
and WP29) when its scenarios land. A scenario still tagged `@WIP` is planned, whatever file it sits in.

## Counter-examples of the ruleset review

| Id | Ruled answer | Decision | Scenario | PR | Status |
|---|---|---|---|---|---|
| A1 | `Req` is the union of the must-have items of every governing copy of a preset id; requirements only tighten | `DM-01M6RA7FHRC5K8QPX7KN5FZ4S8` | `layer-merge.feature` (planned): the A1 union scenario (WP22, WP25) | B | planned |
| A2 | Overrides and requirements iterate to a fixed point on the final set; an override cycle is refused (`override_cycle` at registration, `500 pack_unreadable` at read) | `DM-01M6RA7M56M1409EV9JX1EW83K`, `DM-01M6RN6BWE6SVZZE8ERQ8Z9MV6` | `effective-reads.feature` (planned): the pinned final state of A2 (WP23) and a refused override in `layer-merge.feature` | B | planned |
| A3 | Overrides compose transitively: `F` over `E` over `T` leaves `F`, which hides `E` and `T` and satisfies a requirement on `T` | `DM-01M6RA7RPNZS810FTFC98Z13GH` | `effective-reads.feature` (planned): the `F -> E -> T` chain leaves `F` | B | planned |
| A4 | A replace judges the floor only on the deny entries it adds and drops ids the packs no longer hold, reported in `dropped` | `DM-01M6RA7XC5HC8915GZT2R3H83W` | `activation-ruling-changes.feature`: `A4 - a GET then PUT of a body whose deny entry a pack now requires is 200 with the same ETag`; `A4 - a PUT of the body read before a pack upgrade removed an id drops the id and lists it in dropped` | A0 | built |
| A5 | Registration runs an impact check over every closure the pack joins and is refused `422`, naming the packs | `DM-01M6RA8225EXG6GAT1MM2R9T6C` | `registration/whole-pack-registration.feature` (planned): a registration that breaks a lock is refused naming both packs (WP15, US1 AS8) | A | planned |
| A6 | One gate for both override paths: an `overrides` edge onto an artifact that is not open is refused at registration | `DM-01M6RA86QPBM1VE5QZTB9AZ195` | `registration/whole-pack-registration.feature` (planned): an override of an extendable or locked artifact is refused (WP15) | A | planned |
| A7 | Mission types, steps and agent profiles follow the same activation, override and enhancement rules as any artifact; `activatedKinds` and `missionTypes` are refused, storage is version 2 (Q1 to Q4) | `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`, `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`, `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC`, `DM-01M6RCY40CQQTHC130RWF43W0D`, `DM-01M6RCY8MYNGNPJ247M2Y4880N`, `DM-01M6RFGMDH859CJP3RHHF9YC3H`, `DM-01M6RMJHDKNTXAM055Q8WR6SDN` | `activation-kind-model.feature`: `Selecting a mission type with a cascade selects what its steps reach`; `Selecting a mission type without a cascade selects the mission type alone`; `A cascading deactivate of a mission type unselects what only it kept`; `A deny entry on a mission type is accepted`; `A deny entry on a required mission type is 409 artifact_required and stores nothing`; `A deny entry on a locked mission type is 409 artifact_locked and stores nothing`; `A document that denies a required mission type is 409 on create, and one that denies another is 201`; `activatedKinds in a create body is 422 validation_failed naming selected.<kind>`; `missionTypes in a create body is 422 validation_failed naming selected.mission_type`; `A replace that sends both remnant members is 422 with one violation per member and stores nothing`; `A preset naming a skill applies it`; `A PUT that sends [] for the selected kinds a GET answered as absent keeps the ETag and the stored bytes`; `A stored version 1 document reads back as its version 2 upgrade`; `Reads leave a stored version 1 document untouched, and the next conditional write stores version 2` | A0 | built |
| A7 (path id) | A mission type's path id is read in any spelling and stores the declared id, as any kind's does | `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY` | `activation-selections.feature`: `A mission type path id names it in any known spelling and stores the declared id, as any kind's does` | A0 | built |
| A7 (preset) | A preset replaces the mission types it lists as `selected.mission_type` | `DM-01M6RCY40CQQTHC130RWF43W0D` | `activation-presets.feature`: `A preset that lists mission types replaces selected.mission_type` | A0 | built |
| A8 (registration) | An `enhances` cycle, and an enhancement with no real target, is refused at registration | `DM-01M6RA8G0RGRWSFW4DERH8W319` | `registration/whole-pack-registration.feature` (planned): an enhancer ring is refused (WP15) | A | planned |
| A8 (read) | An enhancer is in force only when its support reaches a non-enhancer in force (least fixed point), so a ring is not in force | `DM-01M6RA8G0RGRWSFW4DERH8W319`, `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ` | `effective-reads.feature` (planned): an enhancer ring is not in force (WP22) | B | planned |
| A9 | An enhancer or step bound to something not in force is dropped from the floor and reported (`requiredNotInForce`) | `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ`, `DM-01M6RFGFW3S0YJ222QMHD2W11B` | `effective-reads.feature` (planned): the bound and ungrounded floor (WP22) | B | planned |
| A10 | The floor holds only the must-have items a pack of the closure holds; a held item of the same preset stays required | `DM-01M6RA8MG55V8C2V9PXWDTRNQS` | `activation-ruling-changes.feature`: `A10 - a must-have item no pack holds is not on the floor, and a held one of the same preset is` | A0 | built |
| A10 (delete) | An artifact another pack's preset names cannot be deleted (`409`) | `DM-01M6RA8MG55V8C2V9PXWDTRNQS` | `activation-ruling-changes.feature` (planned): A10 - an artifact another pack's preset names cannot be deleted, `@WIP` until WP19; also `pack-removal.feature` | A | planned |
| A10 (report) | The unheld must-have item is listed in `requiredNotHeld` | `DM-01M6RA8MG55V8C2V9PXWDTRNQS` | `activation-ruling-changes.feature` (planned): A10 - the unheld must-have item is in requiredNotHeld, `@WIP` until WP25; also `effective-reads.feature` | B | planned |
| A11 | An artifact that both enhances and overrides one target is refused at registration | `DM-01M6RA8S9G57W4BW55SNVHBQ10` | `registration/whole-pack-registration.feature` (planned): a dual-edge artifact is refused (WP15) | A | planned |
| A12 | An `overrides` edge whose re-pointed target is its own source is refused at registration | `DM-01M6RA8S9G57W4BW55SNVHBQ10` | `registration/whole-pack-registration.feature` (planned): a self-override is refused (WP15) | A | planned |
| A13 | A cascading deactivate removes the target and what its walk reached unless something else active keeps it; prior selections are not remembered (accepted and documented) | `DM-01M6RA8XSM9CZZX1ADX6G80T3X` | `effective-reads.feature` (planned): the A13 scenario (WP22); the note is written by WP20 | B | planned |
| A14 | `enhances` is no sibling conflict; a settlement names exactly one winner | `DM-01M6RA92BS6GGCYTRCFQDYYY5S` | `activation-ruling-changes.feature`: `A14 - two org packs that enhance one artifact compose` | A0 | built |
| A15 | A pack's policy locks only the artifacts of the pack and of the packs it builds on | `DM-01M6RA97220XBR7RR447FE2JXD` | `activation-ruling-changes.feature`: `A15 - a project pack's kind default locks its own and its ancestors' directives, not an unrelated pack's` | A0 | built |
| B1 (writes) | A write drops a version pin no pack holds into `droppedPins`; a create keeps `422 version_not_held` | `DM-01M6RA9BKJY7X9BFV93QW1KM1T`, `DM-01M6RN5Y406SB4KA13QYMQ2P8J` | `activation-ruling-changes.feature`: `B1 - a per-artifact write drops a pin no pack holds into droppedPins, and a create refuses it` | A0 | built |
| B1 (history) | Every version a pack ever held is remembered, so a version is never reused with other content (`409 pack_version_conflict`) | `DM-01M6RA9BKJY7X9BFV93QW1KM1T` | `registration/whole-pack-registration.feature` (planned): a version re-registered with other content is refused (WP15, US1 AS3) | A | planned |
| B1 (reads) | A read reports a pin no pack holds in `pinNotHeld` | `DM-01M6RA9BKJY7X9BFV93QW1KM1T` | `effective-reads.feature` (planned): a pin no pack holds is in `pinNotHeld` (WP22) | B | planned |
| B2 | Keep eager dropping: stale ids drop on any write, reported in `dropped` | `DM-01M6RA9G56DTA5GFY5Z28S0DB1` | `activation-selections.feature`: `A write drops the ids the closure no longer declares and reports them in dropped` | A0 | built |
| B3 | A whole-pack replace checks references against the new tree, and every `409` lists the referrers | `DM-01M6RA9MQ6YAP048KD43DNFS4F` | `pack-removal.feature` (planned): a `409` that names the referring packs and artifacts (WP19, US2 AS2) | A | planned |
| B4 (registration) | A registration reports the floor and lock changes and the activations whose in-force set changes | `DM-01M6RA9SAKCBSKGX165JM3CAGS` | `registration/whole-pack-registration.feature` (planned): the notices of a successful registration (WP15, US1 AS9) | A | planned |
| B4 (read) | `GET /activations/{id}` lists a deny entry the floor overrides in `denyNotApplied` | `DM-01M6RA9SAKCBSKGX165JM3CAGS` | `effective-reads.feature` (planned): a deny entry the floor overrides, with an applied deny as the control (WP22) | B | planned |
| B5 | `includeDeactivated` clears deny entries only on artifacts the walk reaches and selects, not on lineage ancestors it passes | `DM-01M6RA9XQSH3E07C7VCPCVD6R4` | `activation-ruling-changes.feature`: `B5 - includeDeactivated clears the deny entry of a reached artifact and keeps a passed ancestor's` | A0 | built |
| B5 (selections) | The same, in the selections scenarios: a denied ancestor inside the scope is passed through and keeps its deny entry | `DM-01M6RA9XQSH3E07C7VCPCVD6R4` | `activation-selections.feature`: `includeDeactivated passes through a denied ancestor inside the scope and keeps its deny entry` | A0 | built |
| B6 | A write reports an artifact it applied that the read will not put in force (`warnings`: `enhanced_target_not_in_force`, `overridden`) | `DM-01M6RAA2B2G9PNXN2F9SJ7B1CR` | `effective-reads.feature` (planned): a select of an enhancer whose target is denied warns (WP22, WP25) | B | planned |
| B7 | A select brings its artifact into force, so a select needs no change to a kind gate (the gate is gone in A0); the `languages` exception stays | `DM-01M6RAA2B2G9PNXN2F9SJ7B1CR`, `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY` | `effective-reads.feature` (planned): a select on an empty activation is in force (WP22) | B | planned |
| B8 | A pack's own policy never refuses its own write, and binds every pack built on it | `DM-01M6RAA6Y7NVVGKVKQV05GZYES` | `activation-ruling-changes.feature`: `B8 - a pack's own lock does not refuse its own per-artifact PUT, and binds the pack built on it` | A0 | built |

## Rulings of 2026-11-07 (#408) and the relaxed write rule

The depth ruling is PR A0 (WP30); the read-side rulings are folded into the requirements of PRs B and C.

| Id | Ruled answer | Decision | Scenario | PR | Status |
|---|---|---|---|---|---|
| #408 depth 0 and 1 | `depth` counts `suggests` hops only: `0` selects the target and its hard closure, `1` adds the first `suggests` target and its closure | `DM-01M6TDH0SY5Q224GX709DXWG8K` | `activation-cascade-depth.feature`: `A cascading select at depth 0 selects the target and its hard closure only`; `A cascading select at depth 1 adds the first suggests target and its hard closure`; `An artifact reached over requires is selected at depth 0 although a suggests path also reaches it` | A0 | built |
| #408 unlimited | An absent `depth` walks the whole chain; a depth the chain does not reach has an empty `skippedByDepth`; a `depth` without a cascade has no effect | `DM-01M6TDH0SY5Q224GX709DXWG8K` | `activation-cascade-depth.feature`: `A cascading select without depth walks the whole suggests chain`; `A cascading select at a depth the chain does not reach has an empty skippedByDepth`; `A depth without a cascade is accepted and has no effect` | A0 | built |
| #408 deactivate | A cascading deactivate honours `depth` in the target's own walk, and undoes a select at the same depth in what it removes | `DM-01M6TDH0SY5Q224GX709DXWG8K`, `DM-01M6TF3BA808JMW0W0A0CPXBNW` | `activation-cascade-depth.feature`: `A cascading deactivate at depth 1 undoes a cascading select at depth 1`; `A cascading deactivate at depth 0 leaves what lies past the bound selected`; `A cascading deactivate without depth removes the whole chain` | A0 | built |
| #408 round trip | The round trip is not exact when another active artifact suggests a candidate: the candidate is kept in `skippedShared` | `DM-01M6TF3BA808JMW0W0A0CPXBNW` | `activation-cascade-depth.feature`: `The round trip is not exact when another active artifact suggests a candidate` | A0 | built |
| #408 refusals | A `depth` that is a string, a decimal, negative or past the int32 range is `422` at `/depth` and stores nothing | `DM-01M6TDH0SY5Q224GX709DXWG8K` | `activation-cascade-depth.feature`: `A select whose depth is a string is 422 at /depth and stores nothing`; `A select whose depth is a decimal is 422 at /depth and stores nothing`; `A select whose depth is a negative number is 422 at /depth and stores nothing`; `A select whose depth is a number past the int32 range is 422 at /depth and stores nothing`; `A deactivate whose depth is negative is 422 at /depth and stores nothing`; `A deactivate whose depth is a decimal is 422 at /depth and stores nothing` | A0 | built |
| #408 language filter | The language filter applies at read only; a read with no activation applies no filtering | operator ruling on #408 (2026-11-07), ruling 2 | `effective-reads.feature` (planned): the language filter on the effective reads (FR-013, FR-017) | B | planned |
| #408 conditional suggests | A `suggests` edge's `when` surfaces as conditional in the effective reads, above all the action context; hard relations stay plain entries | operator ruling on #408 (2026-11-07), ruling 3 | `effective-graph-and-context.feature` (planned): a `suggests`-only artifact listed with its `when` and `reason` (FR-019); also `effective-reads.feature` (FR-013) | C | planned |
| Q9 (deny satisfied by an override) | A deny entry on a required, not locked artifact is accepted when an active override of it is in the document after the write | `DM-01M6R3YX23H46PXGNEFC31E6CT` | `activation-deny-satisfied.feature` (planned): deactivate required `T` with an active `E overrides T` is `200` (WP29) | C | planned |

## Acceptance scenarios of the user stories

User stories 1 and 2 are PR A, stories 3, 4 and 6 are PR B, and story 5 is PR C. None is built in PR A0: the effective
reads still answer `501` and the registration and removal operations are not built until their pull requests.

| Id | Ruled answer | Scenario | PR | Status |
|---|---|---|---|---|
| US1 AS1 | `PUT /packs/p` of a new pack answers `202`; polling ends `succeeded` (`created`) and everything is readable | `registration/whole-pack-registration.feature` (planned) | A | planned |
| US1 AS2 | A 1.1.0 without `B` ends `replaced`; `B` and its edges are gone | `registration/whole-pack-registration.feature` (planned) | A | planned |
| US1 AS3 | 1.0.0 sent again with other content ends `refused` `409 pack_version_conflict` | `registration/whole-pack-registration.feature` (planned) | A | planned |
| US1 AS4 | The identical 1.0.0 ends `succeeded` as `unchanged` | `registration/whole-pack-registration.feature` (planned) | A | planned |
| US1 AS5 | An unknown file, a path that leaves the pack or an invalid artifact ends `refused` `422`; nothing is stored | `registration/whole-pack-registration.feature` (planned) | A | planned |
| US1 AS6 | A stale `If-Match` is `412` synchronously; a revision that moves after acceptance ends `refused` `412` | `registration/whole-pack-registration.feature` (planned) | A | planned |
| US1 AS7 | `POST /packs` naming a source answers `202`, fetches, validates and replaces | `registration/whole-pack-registration.feature` (planned) | A | planned |
| US1 AS8 | A registration that breaks a lock is refused with the impact violation naming both packs | `registration/whole-pack-registration.feature` (planned) | A | planned |
| US1 AS9 | A successful registration lists the floor and lock changes and the activations affected | `registration/whole-pack-registration.feature` (planned) | A | planned |
| US2 AS1 | `DELETE` of an artifact answers `204`; the artifact, its edges and its preset item are gone | `pack-removal.feature` (planned) | A | planned |
| US2 AS2 | `DELETE` of an artifact another pack references answers `409` naming the referrer | `pack-removal.feature` (planned) | A | planned |
| US2 AS3 | `DELETE` of a relationship answers `204`; the artifacts remain | `pack-removal.feature` (planned) | A | planned |
| US2 AS4 | `DELETE` of an artifact the pack does not hold answers `404` | `pack-removal.feature` (planned) | A | planned |
| US3 AS1 | One effective `D`, with the project version's content and provenance, no inherited field | `layer-merge.feature` (planned) | B | planned |
| US3 AS2 | A governing preset's must-have `D` stays in force (`required`) under a deny; the deny is reported as not applied | `effective-reads.feature` (planned) | B | planned |
| US3 AS3 | A selected `X` no pack holds is absent and listed as stale | `effective-reads.feature` (planned) | B | planned |
| US3 AS4 | A mission type's steps are in force only while it is; `activatedKinds` no longer exists | `effective-reads.feature` (planned) | B | planned |
| US3 AS5 | `E enhances T`: both in force when `E` is active; `E` absent when not active (no pull) | `effective-reads.feature` (planned) | B | planned |
| US3 AS6 | An active `E overrides T` hides `T`, reported `overridden` | `effective-reads.feature` (planned) | B | planned |
| US3 AS7 | A matching `If-None-Match` answers `304` | `effective-reads.feature` (planned) | B | planned |
| US4 AS1 | Each case appears once in the bucket that explains it, with the activation's revision | `effective-reads.feature` (planned) | B | planned |
| US4 AS2 | An unknown activation id is `404 activation_not_found` | `effective-reads.feature` (planned) | B | planned |
| US5 AS1 | An edge whose endpoint is not in force is absent from the effective graph | `effective-graph-and-context.feature` (planned) | C | planned |
| US5 AS2 | A layer override resolves edges to the winner and is listed in `layerOverrides` | `effective-graph-and-context.feature` (planned) | C | planned |
| US5 AS3 | `T` read from its side shows `E enhances T` | `effective-graph-and-context.feature` (planned) | C | planned |
| US5 AS4 | The action context of a mission type in force holds what the step contract names, with active enhancers | `effective-graph-and-context.feature` (planned) | C | planned |
| US5 AS5 | A mission type not in force gives an empty context and the reason; an unknown action is `404 action_not_found` | `effective-graph-and-context.feature` (planned) | C | planned |
| US6 AS1 | A settlement that names `b` puts `b`'s `D` in force and raises no `pack_sibling_conflict` | `layer-merge.feature` (planned) | B | planned |
| US6 AS2 | The same conflict without the entry refuses with `500 pack_unreadable` naming `pack_sibling_conflict` | `layer-merge.feature` (planned) | B | planned |

## Checking the A0 rows

Every built row names a scenario that exists in the file named. To check, list the scenario names of each file
(`grep -E '^\s*Scenario' contracts/charter/scenarios/<file>`) and compare them with the scenario names above.
