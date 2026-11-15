---
affected_files: []
cycle_number: 1
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-14T11:46:51Z'
reviewer_agent: claude-reviewer
wp_id: WP07
---

# WP07 review, cycle 1: changes requested

Reviewed commit 0df44cfad (against 60955ce6a). The schema, domain, codec, load and validate rules, compose check,
removal guard and reads are sound, and `:packs:test`, `:missions:test` and `:missions-edge:test` pass when forced to
rerun. Three MEDIUM findings block approval.

## MEDIUM

**Issue 1: the example-pack repoints contradict WP01 T004 and leave the guidance contradicting the candidate.**
`service/packs/src/testFixtures/resources/example-packs/built-in/missions/software-dev/software-dev.mission-type.yaml`
(implement substeps `workspace`, `branching-paradigm`, `branching-procedure`; specify `map-examples`).
T004 says to repoint "keeping the intent where you can" and to "add a small artifact only where no held artifact
fits". For the candidates below, no held artifact fits, and the repoints change what the substep means:

- `trunk-based` (paradigm) became `atomic-design`, a UI design paradigm. The substep is "Apply trunk-based
  development".
- `git-flow` (procedure) became `action-item-attribution-gate`. The guidance still says "Follow its feature, release,
  and hotfix branch workflow".
- `execution-lanes` (toolguide) became `efficient-local-tooling`. The guidance still says "The execution-lanes
  toolguide describes the only supported git worktree mechanism".
- `example-mapping-workshop` (procedure) became `action-item-attribution-gate`.

The added scope edges also put these artifacts in force for the implement and specify contexts. The example pack is
the round-trip corpus and the reference shape, so it should not read as nonsense.

Fix: add small artifacts for these four, as T004 allows: `procedure:git-flow`, `paradigm:trunk-based`,
`toolguide:execution-lanes` and `procedure:example-mapping-workshop`. Give each its scope edge in `action.graph.yaml`,
drop the scope edges that are no longer needed, and restamp the manifest with the repository tooling. Review the other
repoints the same way and keep the ones that fit, such as the DIRECTIVE_029/033 to 003/010 and the
supply-chain-install-safety to architectural-gate-non-vacuity repoints.

**Issue 2: no test covers the registration-time refusal of a qualified candidate (FR-005).**
`service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationImpact.java`, in `inClosure`: the new
branch maps a compose `pack_unreadable` made only of `substep_delegation_unresolved` violations to
`422 validation_failed`. FR-005 says "a qualified candidate is checked at compose and at registration", and
`CharterRefusalCode.yaml` and the change fragment promise the 422. But no unit test or scenario exercises this branch.
The only qualified-candidate scenario is the composing read (`GET /offering` answers 500).

Fix: add a scenario to `mission-types.feature`. It registers a pack whose substep names `base::tdd`, where `base` is in
its depends_on closure and does not hold `tactic:tdd`. Assert `422 validation_failed` with one violation,
`substep_delegation_unresolved`, at the mission type file, then the fixed archive registering successfully. Or add an
equivalent `RegistrationImpact` test.

**Issue 3 (checklist item 1, dead code): two public members have no production caller.**
`service/missions/src/main/java/ai/speckitty/charter/missions/domain/SubstepCandidate.java`:

- `reference(ArtifactKind)` (line 95) is called only from `SubstepTest`.
- `GRAMMAR` (line 31) is referenced nowhere, test or main. Its Javadoc says "The mission-type schema states the same
  pattern", but nothing checks that.

Fix: delete both, or give them a live caller. For example, a test could pin `GRAMMAR` to the candidate pattern in
`mission-type.schema.json`, so the two cannot drift.

## LOW (fold or note; not blocking on their own)

- **Pack-scope link collision.** `MissionBodies.substepCandidate` together with `PacksMissionTypes.ownCandidates` keys
  held candidates by kind and id only. A type with a bare `x` and a qualified `other::x` of the same kind links
  `other::x` to the pack's own `x` on `getPackMissionStep`. The contract says a qualified candidate on a pack's step has
  empty `_links`. Check `candidate.qualified()` in the pack scope.
- **Own-name candidates.** A candidate written with the pack's own name (`qa::x`) is held at load but gets no
  `artifact` link on the pack read, because `ownCandidates` filters every qualified candidate.
- **`DUPLICATE_STEP_ID` split.** Validate mode reports `duplicate_step_id` for a repeated substep id, while the load
  reports the generic `domain` violation. This is documented, and the plant is still a step contract (declared
  deviation 7). WP08 should retarget the plant to a substep and drop the step-contract half of the code's doc.
- **Duplicate candidates.** Neither `candidates` in `mission-type.schema.json` nor the domain refuses a duplicate
  candidate. Consider `uniqueItems: true` with a matching domain rule.
- **Removal guard tests.** The `deletePackRelationship` guard (`substep_delegation_unscoped` at `/to`) has unit tests
  only, with no scenario.

## Declared deviations

1. Kebab-case substep ids: accepted. T030 requires the kernel identifier grammar, and the charter's model is canonical.
   The open-packs drafts (FR-020) must rename the 28 contracts' underscore ids too.
2. Repoints that do not keep the intent: rejected, see Issue 1.
3. Duplicate candidates dropped: accepted as a consequence of repointing. Revisit after Issue 1.
4. The `finalize` substep on the on-demand step `tasks-finalize`: accepted. `ExamplePackCoverageTest` needs an example
   at `/on_demand_steps/*/substeps/...`.
5. `optional` as `const: true`: accepted. It is consistent with `blocking`, and the read still serves a boolean.
6. The step-contract files left in place until WP08: accepted, per the WP's Risks section. No reader merges the two
   homes.
7. The duplicate-step-id plant is still a step contract: accepted for now, see the LOW item above.
8. The new port `PackRemovalStore.missionTypes`: accepted.

## Anti-pattern checklist

1. Dead code: FAIL (Issue 3).
2. Synthetic fixture: PASS. The plants go through the real validate, load and staged load, and the scenarios go
   through HTTP.
3. Silent empty return: PASS. `SubstepDelegationRule.reference` returns empty only for input the schema refuses, and
   says so.
4. FR coverage: FAIL for FR-005's registration path (Issue 2). FR-004 passes.
5. Frozen surface: PASS.
6. Locked decision: PASS. No reader merges the step contract and the substeps.
7. Shared-file ownership: PASS. The `mission-types.feature` rows (WP06's file) are noted in the commit message.
8. Production fragility: PASS.
