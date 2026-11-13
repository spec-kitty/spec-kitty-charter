---
affected_files: []
cycle_number: 1
mission_slug: effective-step-and-action-context-01M774HB
reproduction_command:
reviewed_at: '2026-11-12T22:01:08Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP07
---

# WP07 review feedback, cycle 1

**Verdict: changes requested (one MEDIUM).**

## MEDIUM: 409 before `prompt_slot_missing` is not pinned end to end

The refusal order R10 puts `409 mission_type_not_in_force` before the 500 slot check. Only
`EffectiveReadServiceTest` (over `FakeAvailablePacks`) pins it. Two mutants survive the contract suite:

- M1: move `session.inForce(asked)` above the 409 check in `EffectiveReadService`: caught by the unit test only.
- M8: make `ClosureViewSession.isInForce` use `statesOf(query)` (runs `requireSlotsOnce`): survives every test.

Failure scenario: software-dev in force with a missing slot, research held but not selected:
`GET …/research/actions/gather/prompt` would answer 500 `prompt_slot_missing` instead of 409.

Required fix, in the last scenario of `contracts/charter/scenarios/effective-step-prompt.feature`, after
`And the field at "/detail" contains "spdd:reasons-block"`:

```
When I GET "/activations/a-3/mission-types/research/actions/gather/prompt" with the header "If-None-Match": "*"
Then the response status is 409
And the problem code is "mission_type_not_in_force"
```

Verified: passes at HEAD, fails with M8 applied (500 `prompt_slot_missing`).

## LOW (fold, no action required)

- `ClosureQueriesTest` writes `java.util.Collection` fully qualified; import it.
- The `governanceFingerprint` change for multi-pack closures has no fragment; acceptable (unreleased, lands with ETag v3). WP08 records it.
