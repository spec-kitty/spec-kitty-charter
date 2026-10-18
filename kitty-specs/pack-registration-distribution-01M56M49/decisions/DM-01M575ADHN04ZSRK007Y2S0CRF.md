# Decision Moment `01M575ADHN04ZSRK007Y2S0CRF`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.entrypoints.mcp-adapter`
- **Input key:** `mcp_adapter`
- **Status:** `resolved`
- **Created:** `2026-10-18T09:26:09.717327+00:00`
- **Resolved:** `2026-10-18T09:45:39.265463+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Existing docs plan an MCP entry-point adapter; the brief named only REST+GraphQL. MCP fate?

## Options

- Drop MCP from this design
- Defer MCP as a later third adapter over the same use cases
- Include MCP now as a third sibling adapter

## Final answer

Defer MCP as a later third entry-point adapter; keep the use-case layer adapter-ready. Not built in this design.

## Rationale

_(none)_

## Change log

- `2026-10-18T09:26:09.717327+00:00` — opened
- `2026-10-18T09:45:39.265463+00:00` — resolved (final_answer="Defer MCP as a later third entry-point adapter; keep the use-case layer adapter-ready. Not built in this design.")
