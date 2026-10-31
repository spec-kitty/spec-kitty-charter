# Decision Moment `01M65WZDXPE98PS59E5R9VNS6V`

- **Mission:** `mission-type-consolidation-01M65MCK`
- **Origin flow:** `plan`
- **Slot key:** `plan.q1a.prompt-frontmatter-and-bound`
- **Input key:** `plan.q1a.prompt-frontmatter-and-bound`
- **Status:** `resolved`
- **Created:** `2026-10-30T07:56:48.438277+00:00`
- **Resolved:** `2026-10-30T07:56:50.701471+00:00`
- **Resolved by:** `Stijn Dejongh (operator), relayed by the orchestrator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

Q1 refined: does a prompt keep its frontmatter, and is a step description bounded?

## Options

- drop prompt frontmatter, unbounded description
- keep prompt frontmatter as a detailed description, bound the step description

## Final answer

The step description in the mission-type file is the description, at most 160 characters (schema maxLength and a domain twin; pinned descriptions run 19 to 119). A prompt keeps its frontmatter description as its detailed description, stored and round-tripped; any other frontmatter key, or guidelines frontmatter, is refused. Sourcing stands: the runtime text first, the prompt frontmatter only where no runtime text exists; such steps are flagged in the open-packs issue. Supersedes the 'prompt frontmatter is not stored' part of plan.q1.

## Rationale

Operator refinement 2026-10-30, verbatim: 'step description in mission type charter config contains the description. the prompt frontmatter may stay to allow for more detailed descriptions. Step descriptions should be length-bound (choose a sensible default)'.

## Change log

- `2026-10-30T07:56:48.438277+00:00` — opened
- `2026-10-30T07:56:50.701471+00:00` — resolved (final_answer="The step description in the mission-type file is the description, at most 160 characters (schema maxLength and a domain twin; pinned descriptions run 19 to 119). A prompt keeps its frontmatter description as its detailed description, stored and round-tripped; any other frontmatter key, or guidelines frontmatter, is refused. Sourcing stands: the runtime text first, the prompt frontmatter only where no runtime text exists; such steps are flagged in the open-packs issue. Supersedes the 'prompt frontmatter is not stored' part of plan.q1.")
