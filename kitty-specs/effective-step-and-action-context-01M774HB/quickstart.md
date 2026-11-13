# Quickstart: checking slice C by hand

Run the service over the example packs (`service/packs/src/testFixtures/resources/example-packs`), with an
activation that selects `mission_type:software-dev`, `step_enhancement:plan-risk-notes` and
`step_enhancement:spdd-plan`.

1. **Prompt.** `GET /activations/{id}/mission-types/software-dev/actions/plan/prompt` answers 200: the plan prompt
   as stored, one slot `spdd:reasons-block` with two fragments, `plan-risk-notes` (built-in) then `spdd-plan` (org).
2. **Not in force.** Deselect the mission type; the same read answers `409 mission_type_not_in_force`, while
   `.../actions/plan/context` answers 200 with no artifacts.
3. **Context.** `GET .../actions/implement/context` lists each item with `appliesThrough`, and `_links.prompt`.
4. **Compose refusal.** Register a project pack that layer-overrides `software-dev` with a plan prompt that has no
   `spdd:reasons-block` slot while `spdd-plan` is in force: `422 prompt_slot_missing`.
