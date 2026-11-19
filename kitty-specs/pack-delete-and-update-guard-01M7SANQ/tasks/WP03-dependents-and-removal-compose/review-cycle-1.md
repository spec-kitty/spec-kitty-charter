---
affected_files: []
cycle_number: 1
mission_slug: pack-delete-and-update-guard-01M7SANQ
reproduction_command:
reviewed_at: '2026-11-19T10:13:50Z'
reviewer_agent: reviewer-claude
wp_id: WP03
---

# WP03 review (independent reviewer, cycle 1): REJECT

Target: feat/517-pack-delete-and-update-guard @ 85d47e3. `./gradlew verifyAll` green.

## R3-1 (MEDIUM): the activation dependents fail open
`ClosurePackDependents.activationsNaming` reads `ActivationImpact.activationRoots()`. Its javadoc says
"An activation whose document cannot be read ... is left out", and `FileActivationRepository.list()`
(line ~167) does skip an `ActivationStorageException` document. So a stored activation whose `packs` list
names the pack, but whose document does not read, does not block the delete. P3 / squad M3 say "a dependent
that cannot be read never lets a delete through", and the code fails closed for unreadable *packs* only.
Fix: either fail the removal closed (`500`, naming the activation) when any stored activation cannot be read,
or document it as an operator-ruled exception in ADR-0081 and the contract. Add a unit test either way.

## Sound
- P1 (port replaced outright, `List<ActivationRoots>`): sound and charter-conformant.
- D6 (only activations that name the pack are dependents; the strict closure compose still runs via
  `checkRemoval` on one session): implemented as ruled.
- M5 as documented behavior: sound. Its scenario is a WP04 finding (R4-4).
