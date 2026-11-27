---
work_package_id: WP07
title: 'Slice 4c: REFERENCES on a PackGraph walk (#639)'
dependencies:
- WP05
requirement_refs:
- FR-007
- NFR-001
planning_base_branch: feat/adr-0082-graph-walk-slices
merge_target_branch: feat/adr-0082-graph-walk-slices
branch_strategy: Planning artifacts for this mission were generated on feat/adr-0082-graph-walk-slices. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/adr-0082-graph-walk-slices unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0082-graph-walk-slices-01M8A867
base_commit: d1fd7e04445baff1c1be4991da6149a10b4ebae9
created_at: '2026-11-27T06:15:52.850963+00:00'
subtasks:
- T026
- T027
- T028
phase: Phase 3 - Slice 4
history:
- at: '2026-11-27T00:00:00Z'
  actor: claude::java-jenny
  action: Prompt added after operator ruling 01M8DT4CGSQZBPJCC6K9CN3ZX4
agent_profile: java-jenny
authoritative_surface: service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ReferencesWalkTest.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ReferencesWalkTest.java
- docs/changelog/2026-11-27-references-run-on-a-graph-walk.md
execution_mode: code_change
owned_files:
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ReferencesWalkTest.java
- docs/changelog/2026-11-27-references-run-on-a-graph-walk.md
tags: []
tracker_refs: []
---

# Work Package Prompt: WP07 - Slice 4c: REFERENCES on a PackGraph walk (#639)

## Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else.

## Objective

`PackRulesets.references(..)` today feeds the `ValidationWalker` its members directly
(`visitor -> members.forEach(visitor::onNode)`), with `PackUnderResolution` nodes. Main's landing of #638 recorded this as
"Interim" in `PackRulesets`' Javadoc and on the architecture page, and said slice 4 moves it onto a graph walk. The
operator ruled (decision 01M8DT4CGSQZBPJCC6K9CN3ZX4, 2026-11-26) that #639 delivers it: `REFERENCES` runs on
`PackGraph#walk`, with a walk mode for the members and the members chosen by the scope's selection. The Interim notes
are removed. Codes, details, origins and the order of findings are unchanged (NFR-001); no prior ruling changes here.

Requirements: FR-007, NFR-001, C-001. Design: `research.md` R-2 (modes decide the edges, a scope only selects).

## Subtasks

### T026 A walk mode and scope for the members

- Add the walk mode `REFERENCES` needs to `PackGraph.Walk` (for example `MEMBERS`: each selected pack's node, no edges),
  or show that `PACKS` with the scope's selection already hands over exactly the members; record which in the commit.
- The members are the scope's selection over the roots' composition closure. The walk hands them over in the graph's
  order. Prove, against today's callers, that this is the order they pass today:
  - registration's composing judge (`OfferingReadService`, about line 428) passes every member of the closure it
    composes;
  - pack-doctor's `--full` (`packvalidation/set/ReferencesPhase`, about line 72) passes `rooted.order()` for both the
    closure and the members.
  If a caller's order differs from the graph's, stop and report; do not change an order of findings.
- Red first: a `PackGraphWalkTest` case (or `ReferencesWalkTest`) that pins the mode's visit order and selection.

### T027 references(..) on the walk

- `references(..)` builds its phases over `PackGraphNode`s (OnNode on `PackNode`) and runs them through
  `GraphCheck(mode, phases).run(graph, scope)`; the member's facts come from the graph (`PackGraph.of(.., factsOf)`)
  or the caller's `factsOf`, read once per member as today.
- Its signature may change to take what the walk needs (the closure's nodes, the members as a selection); update the
  three callers (`OfferingReadService`, `ReferencesPhase`, `EdgeIndexTest`) with it. No legacy overload beside the new
  one (the charter's "replace outright").
- The phase gate is still the stop rule: the first check that finds any fault answers all of them.
- Remove the "Interim" paragraph in `PackRulesets#references`' Javadoc and the "Interim: `references(..)` feeds the
  walker its members directly …" lines on `docs/architecture/architectural-patterns-applied.md`; record it as built
  under slice 4.
- `PackGraphWalkBoundaryTest` (who may drive a walk): `references(..)` no longer drives; keep the guard green, and if it
  lists an exception for the direct feed, delete it.

### T028 Tests, parity and docs

- `ReferencesWalkTest` (new): a multi-member closure with a delegation fault, a reference fault and an enhancement
  target fault in different members: the first check's faults only, member by member in the graph's order; a member
  outside the selection is not judged. Red first against today's direct feed where it can be (otherwise say why).
- Every existing `REFERENCES` row stays green: `FullPassParityTest`, `OfferingReadService` composing tests, the
  registration scenarios, `ReferencesPhase` tests.
- Changelog `docs/changelog/2026-11-27-references-run-on-a-graph-walk.md` (description 50 to 180 characters); the
  architecture page's slice 4 built note names it.

## Out-of-map edits (expected, record each in the commit)

`PackGraph.java` and `PackGraphWalkTest` (WP02), `PackRulesets.java` (WP03), `OfferingReadService.java`,
`packvalidation/set/ReferencesPhase.java` (pack-validation), `EdgeIndexTest` (WP05), the architecture page (WP01/WP03).

## Definition of Done

- No `ValidationWalker` run in `packs` or `pack-validation` is fed by hand; `REFERENCES` runs through `PackGraph#walk`.
- No "Interim" note about `references(..)` remains.
- `:packs:test`, `:pack-validation:test`, `:pack-doctor:test`, `:deployable:test` green; `verifyAll`.

## Risks

- Order: the graph's order must equal the callers' order today; a mismatch changes the order of findings (NFR-001).
- pack-doctor reports a fault several roots find once (ruling R-ae): keep that de-duplication where it is.
- A shared `pack_id` held by several packs: the driver visits every holder; the members are configured packs.

Implementation command: `spec-kitty agent action implement WP07 --agent claude::java-jenny --mission adr-0082-graph-walk-slices-01M8A867`
