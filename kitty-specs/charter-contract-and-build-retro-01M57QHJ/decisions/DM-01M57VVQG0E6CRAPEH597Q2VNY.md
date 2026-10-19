# Decision Moment `01M57VVQG0E6CRAPEH597Q2VNY`

- **Mission:** `charter-contract-and-build-retro-01M57QHJ`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.lint-deviation`
- **Input key:** `lint_deviation`
- **Status:** `resolved`
- **Created:** `2026-10-18T16:00:05.632411+00:00`
- **Resolved:** `2026-10-18T16:00:08.993076+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How is the built-in enum-case conflict resolved without a local derived ruleset?

## Options

_(none)_

## Final answer

No local ruleset delta (it repeats the fork-and-drift mistake). Fix it in core: a PR for core #6402 so enum-case accepts the PackTier values, and a core PR so lint_check supports a consumer ruleset that extends core's (counting inherited rules). The charter runs core's ruleset unmodified; CORE_REF moves to the merged core commit; until then the lint step fails honestly on the one known violation. Supersedes the 'scoped, sunset ruleset deviation' part of DM 01M57SQD43YAX5Q3A6QNQBF1TT.

## Rationale

_(none)_

## Change log

- `2026-10-18T16:00:05.632411+00:00` — opened
- `2026-10-18T16:00:08.993076+00:00` — resolved (final_answer="No local ruleset delta (it repeats the fork-and-drift mistake). Fix it in core: a PR for core #6402 so enum-case accepts the PackTier values, and a core PR so lint_check supports a consumer ruleset that extends core's (counting inherited rules). The charter runs core's ruleset unmodified; CORE_REF moves to the merged core commit; until then the lint step fails honestly on the one known violation. Supersedes the 'scoped, sunset ruleset deviation' part of DM 01M57SQD43YAX5Q3A6QNQBF1TT.")
