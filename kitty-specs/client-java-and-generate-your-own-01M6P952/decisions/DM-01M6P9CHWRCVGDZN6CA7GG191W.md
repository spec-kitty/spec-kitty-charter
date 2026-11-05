# Decision Moment `01M6P9CHWRCVGDZN6CA7GG191W`

- **Mission:** `client-java-and-generate-your-own-01M6P952`
- **Origin flow:** `specify`
- **Slot key:** `specify.client.coordinates`
- **Input key:** `client_coordinates`
- **Status:** `resolved`
- **Created:** `2026-11-05T16:41:32.312877+00:00`
- **Resolved:** `2026-11-05T16:41:38.278378+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Which Maven coordinates and helper name does the generated Java client use, given two operator rulings of 2026-10-27 on #217?

## Options

- group ai.speckitty.charter / artifact charter-client (later ruling)
- group ai.speckitty.charter.client / artifact charter-client-java (earlier ruling)
- Other

## Final answer

Package ai.speckitty.charter.client (both rulings agree); Maven group ai.speckitty.charter, artifact charter-client (the later #217 ruling, 2026-10-27 11:09, also recorded in docs/plans/triage-2026-11-02.md); helper ProblemDetails.from(ApiException). Taken as the default in a non-interactive session; raised to the orchestrator and on #217 for confirmation.

## Rationale

_(none)_

## Change log

- `2026-11-05T16:41:32.312877+00:00` — opened
- `2026-11-05T16:41:38.278378+00:00` — resolved (final_answer="Package ai.speckitty.charter.client (both rulings agree); Maven group ai.speckitty.charter, artifact charter-client (the later #217 ruling, 2026-10-27 11:09, also recorded in docs/plans/triage-2026-11-02.md); helper ProblemDetails.from(ApiException). Taken as the default in a non-interactive session; raised to the orchestrator and on #217 for confirmation.")
