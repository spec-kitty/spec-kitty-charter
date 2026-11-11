---
affected_files: []
cycle_number: 1
mission_slug: pack-item-etags-and-resolver-retirement-01M75WRS
reproduction_command:
reviewed_at: '2026-11-11T19:03:37Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP02
---

# WP02 review feedback (cycle 1) — reviewer-renata

## MAJOR-1: the revision-read refusal differs between kinds of the one templated item route
`service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java` `stepContract(...)` (~l.597) calls `revisions.currentRevision(pack)` inside the `read.read` lambda with no mapping. Per `PackRevisions` it throws `PACK_UNREADABLE` for a pack it cannot read. For a tactic the same failure goes through `TacticReadService.revisionOf` -> `packRefusal` and becomes `PACK_NOT_FOUND`, and the other kinds' `*WithRevision` reads do the same. So when the descriptor passes `judgeDescriptor` but the revision cannot be read, `GET /packs/{p}/artifacts/tactic/{id}` answers `pack_not_found` and `GET /packs/{p}/artifacts/mission_step_contract/{id}` answers `pack_unreadable`. That is the same cause on the same route giving different codes.
Fix: give both kinds one mapping, for example wrap the step-contract revision read in the same `PACK_UNREADABLE -> PACK_NOT_FOUND` translation, or decide on the opposite rule for every kind. Add a test that makes `currentRevision` throw `PACK_UNREADABLE` and asserts the same code for a tactic, a directive and a step contract item.
(Note: the step contract's own `packs.load` already passes `PACK_UNREADABLE` through. That predates this WP, but this WP adds a new refusal path to every kind and should not widen the split.)

## MINOR-1: step contract has no NFR-002 or FR-004 coverage
The counting test (asked == 1) and the stale test (revision read inside the refused read, STALE_INPUT, no ETag) cover only the tactic. Add one count assertion for `mission_step_contract`, the one kind whose revision read is wired separately.

## Checked and OK
- `Item.revision()` is non-optional, and the controller sets the ETag on every item.
- StepContractDtoTest only changes constructor arguments.
- The stale controller test asserts 5xx, `stale_input` and no ETag.
- Tests: ArtifactReadServiceTest 58, ArtifactControllerTest 30, TacticReadServiceTest 8, StepContractDtoTest 2, CompositeComponentResolverTest 7. All pass.
