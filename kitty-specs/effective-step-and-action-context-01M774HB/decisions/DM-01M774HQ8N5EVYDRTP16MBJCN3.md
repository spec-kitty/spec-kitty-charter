# Decision Moment `01M774HQ8N5EVYDRTP16MBJCN3`

- **Mission:** `effective-step-and-action-context-01M774HB`
- **Origin flow:** `specify`
- **Slot key:** `specify.scoping.unscoped-in-force`
- **Input key:** `context_scoping_rule`
- **Status:** `resolved`
- **Created:** `2026-11-12T05:44:04.117294+00:00`
- **Resolved:** `2026-11-12T05:47:57.239284+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

#450: is ADR-0018's action scoping meant to drop in-force artifacts no walk from the action reaches (required or project-selected but unscoped)?

## Options

- A: keep the drop, record a divergence, link the effective listing
- B: ownership rule: in-force directive/tactic/paradigm applies when walked, unscoped by any action or mission type, or scoped by the action's mission type (recommended)
- C: required floor only
- Other

## Final answer

B: ownership rule (operator, 2026-11-12, relayed by the orchestrator). An in-force directive, tactic or paradigm applies to mt/a when the walk reaches it, when no action or mission-type node scopes it, or when mission_type:mt scopes it; it brings its closure under ADR-0018 steps 2-5; each entry gets an additive appliesThrough member. Record as an amendment to ADR-0018 D5 and rule E16, plus a divergence entry for honouring mission-type scope. Expected 15 of 16; DIRECTIVE_003 is left out as claimed by other actions.

## Rationale

_(none)_

## Change log

- `2026-11-12T05:44:04.117294+00:00` - opened
- `2026-11-12T05:47:57.239284+00:00` - resolved (final_answer="B: ownership rule (operator, 2026-11-12, relayed by the orchestrator). An in-force directive, tactic or paradigm applies to mt/a when the walk reaches it, when no action or mission-type node scopes it, or when mission_type:mt scopes it; it brings its closure under ADR-0018 steps 2-5; each entry gets an additive appliesThrough member. Record as an amendment to ADR-0018 D5 and rule E16, plus a divergence entry for honouring mission-type scope. Expected 15 of 16; DIRECTIVE_003 is left out as claimed by other actions.")
