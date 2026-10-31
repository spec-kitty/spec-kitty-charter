# Decision Moment `01M65SW09K1HETNSZ22SPKQCXK`

- **Mission:** `mission-type-consolidation-01M65MCK`
- **Origin flow:** `plan`
- **Slot key:** `plan.q1.step-description`
- **Input key:** `plan.q1.step-description`
- **Status:** `resolved`
- **Created:** `2026-10-30T07:02:30.451678+00:00`
- **Resolved:** `2026-10-30T07:02:32.818194+00:00`
- **Resolved by:** `Stijn Dejongh (operator), relayed by the orchestrator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

Q1: A step's description against a prompt's frontmatter description

## Options

- frontmatter wins
- runtime wins
- keep both

## Final answer

The description is for human display, the prompt is what the agent is sent: the step description is the runtime's human overview, else the prompt's frontmatter text; prompt frontmatter is not stored; dropped texts are listed in the open-packs issue.

## Rationale

Operator ruling 2026-10-30, verbatim: 'prompt text is what is sent to the agent, the description is for human display/overview purposes.'

## Change log

- `2026-10-30T07:02:30.451678+00:00` — opened
- `2026-10-30T07:02:32.818194+00:00` — resolved (final_answer="The description is for human display, the prompt is what the agent is sent: the step description is the runtime's human overview, else the prompt's frontmatter text; prompt frontmatter is not stored; dropped texts are listed in the open-packs issue.")
