---
affected_files: []
cycle_number: 2
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-14T08:59:45Z'
reviewer_agent: claude-reviewer
wp_id: WP06
---

# WP06 review, cycle 2 (claude-reviewer): changes requested

Reviewed 78658aff4 (fix) over e9986ede5. `:packs:test` and `:deployable:test` are green; the new compose scenario
(mission-types.feature:1079) runs and passes.

Resolved from cycle 1: Issue 1 (validate mode records facts only for types MissionTypeChecks accepts; the load judges
in the 5-arg `DomainRepositoryService.assembly` only types in `places`; every load path — WholePackStore load and
staged registration, `assemble`/`readable` composing reads, `compositionFacts` — goes through that assembly;
ArtifactFiles uses the 3-arg assembly only for places, and PackCommitStore never reads a pack through
FilesystemPackStore.read, so no path lost the rule). Issue 3 part 2 (prose now states what the compose judges; under A7
the merged type keeps every version's steps, so this is the enforced behaviour). Issue 5 (11-arg PackContents
constructor removed). LOWs: FR-011 in the rule, qualified targets agree in MissionStepLinkRule.stepOf, EdgeIndex.ownSteps
and ownStepLinkTargets, a repeated type id is judged once, the stray plant asserts exact findings in both modes, removal
keeps nodes and `when`/`reason`, EdgeIndexTest name corrected.

**Issue A (MEDIUM): an own-pack-qualified step link is a link to the load but not to the write guards, so
deletePackRelationship can still leave a pack every read refuses.**
`MissionStepLinkRule.ownMissionType` (MissionStepLinkRule.java:122-126) and `EdgeIndex.ownSteps` count a source written
with the pack's own name (`qa::mission_type:T requires action:T/s`) as the type's own link. `PackRemovalStore.Authored
.isOwnStepLink` (PackRemovalStore.java:68-69) now requires `fromPack` empty, and both new guards reuse it:
- `RelationshipRemovalService.refuseUnlinkingAStep` (RelationshipRemovalService.java:195-211): when the registered shard
  holds `qa::mission_type:T requires action:T/s` as the only link of s (the shard is author-stageable and its reader
  accepts qualified ends; a whole-pack registration accepts it because the load counts it), `DELETE` of the bare pair
  matches it through `names()`, `ownLink` is false, nothing refuses, the commit lands and the next load fails
  `mission_step_unlinked` (500 on every read). That is the cycle-1 Issue 2 failure, still open for this spelling.
- `refuseStrayStepLink` (RelationshipRegistrationService.java:142-145) refuses a re-put of a bare link whose step is
  linked only by an own-qualified edge (spurious, but safe).
- Type removal refuses `registration_conflict` for an own-qualified link that the ruling DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ
  says to drop. Cycle 1 asked for "fromPack empty or the pack's own name"; the fix went bare-only, and the test
  `ArtifactRemovalServiceTest.aQualifiedStepLinkIsNoOwnLinkAndStillAConflict` pins the conflict using the pack's own
  name `qa::`, which the rule itself treats as the pack's own.
Fix: make one definition of "own step link" (source bare or the pack's own name; target bare) and use it in the rule,
EdgeIndex, isOwnStepLink and both guards (isOwnStepLink needs the pack name). Change the conflict test to another
pack's name (`base::mission_type:documentation`), add an own-name case that is dropped on type removal, and a
deletePackRelationship test where the shard's only link is own-qualified (refused 422 `mission_step_unlinked`).

**Issue B (MEDIUM): cycle-1 Issue 3 asked for both compose scenarios; the load-path one is missing.**
No scenario (and no unit test) shows a configured pack whose own type has an unlinked step making an activation read
answer `500 pack_unreadable`. This fix moved that check from `FilesystemPackStore.read` into
`DomainRepositoryService.assembly`, so the composing-read seat changed and is now covered by nothing but the
registration path. Add the scenario under an activation in mission-types.feature (a pack whose type file declares a
step its graph does not link; `GET /activations/<id>/graph` -> 500 `pack_unreadable`, detail naming the type file and
the step).

LOW (fold in now or carry):
- MissionTypePlantsTest `aTypeItsChecksRefuseIsNotJudgedForItsLinks` only asserts the code is absent in both modes; it
  does not assert the same findings at the same files, and it uses a repeated step id rather than the missing prompt
  cycle 1 named. Assert the files of both modes match.
- Validate mode lists the stray (graph document) before the unlinked step (type file); the load lists them the other
  way (MissionTypePlantsTest:117-131). Harmless; align if cheap.
- FR-009 is still cited for the step link in EdgeIndexTest.java:142 and ArtifactRemovalServiceTest.java:278 and :524.
- `RelationshipRegistrationService.registerRelationship` javadoc (fault order, ~lines 86-100) and
  `RelationshipRemovalService.remove` @throws do not list the new `validation_failed` / `mission_step_unlinked`.
- contracts/charter/paths/packs_packId_relationships.yaml:277 is 151 characters; reflow.
- put judges the stray link before `policy.authorize` (as WP05's endpoint rule), delete after `gate.authorize`; fine,
  but say so in the prose if the order is part of the contract.
- In `refuseUnlinkingAStep` the authored edges in `kept` can never match: `heldOutsideTheShard` already refuses the
  removal when an authored document holds the pair.
- The remaining PackContents convenience constructors (PackContents.java:90, :130, "call sites that predate the slot
  rules") are the same legacy shape the charter forbids; they predate WP06, so a later cleanup.
- `.kittify/charter/charter.yaml` still lists `TEMPLATE_SET:software-dev-default` (core alignment, carried from cycle 1).
