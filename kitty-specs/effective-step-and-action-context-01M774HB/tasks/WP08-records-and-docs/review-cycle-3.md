---
affected_files: []
cycle_number: 3
mission_slug: effective-step-and-action-context-01M774HB
reproduction_command:
reviewed_at: '2026-11-13T07:22:01Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP08
---

# WP08 reopened: record operator ruling Q3 (2026-11-13)

Q3: the prompt read follows the one place rule. WP09 (lane-h, 86d9fd326 and 25f7bcd4b) makes every judgement of the
prompt in force use `StepHolding.placeOf`: the prompt read, the C38 slot check at reads, registration, impact-checked
writes and activation writes, the step read, the context's `artifact` link and the step contract's `step` link.

Record, with pins that exist on lane-h:
- rules page: E16/A7 and F8 no longer say "pending Q3"; the prompt is at the step's place; C38 judges the prompt at the
  step's place (a closure where the action winner's prompt lacks the slot but the winning version's declares it now
  composes); F6/G8 wording if it names the action winner.
- ADR-0066 d9 annotation: the prompt in force is the stored companion at the step's place (R8 amended by Q3).
- service-contract `getEffectiveStepPrompt` and `getEffectiveMissionStep` rows; changelog page bullet.
- Known edge, recorded (not changed): a step the closure holds that no version at either place declares — the step read
  answers `404 action_not_found`, the context and the prompt read answer `200` (empty prompt).
