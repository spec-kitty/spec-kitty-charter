# Decision Moment `01M599MEAJS44RYEY0X387BY4H`

- **Mission:** `charter-contract-and-build-retro-01M57QHJ`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.internal-error`
- **Input key:** `unexpected_failure_response`
- **Status:** `resolved`
- **Created:** `2026-10-19T05:20:01.362797+00:00`
- **Resolved:** `2026-10-19T05:20:04.194737+00:00`
- **Resolved by:** `stijn`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

How does the contract cover an unexpected 500 that is not a refusal?

## Options

_(none)_

## Final answer

Add internal_error code

## Rationale

Operator ruling 2026-10-19. Every 500 is a CharterRefusal: CharterRefusalCode gains internal_error; detail stays generic so no exception text leaks.

## Change log

- `2026-10-19T05:20:01.362797+00:00` — opened
- `2026-10-19T05:20:04.194737+00:00` — resolved (final_answer="Add internal_error code")
