# Decision Moment `01M60PM2ST1YTN73311DHZM7PC`

- **Mission:** `procedure-antipattern-toolguide-round-trip-01M60H9B`
- **Origin flow:** `plan`
- **Slot key:** `plan.api.guide-uri-handling`
- **Input key:** `guide_uri_handling`
- **Status:** `resolved`
- **Created:** `2026-10-28T07:29:32.986310+00:00`
- **Resolved:** `2026-10-28T07:29:35.816431+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

When registration sends a URI, what does the service do?

## Options

_(none)_

## Final answer

Fetch the guide from the URI and store its content as the Markdown companion (needs allowlist, size/time limits, SSRF protection).

## Rationale

_(none)_

## Change log

- `2026-10-28T07:29:32.986310+00:00` — opened
- `2026-10-28T07:29:35.816431+00:00` — resolved (final_answer="Fetch the guide from the URI and store its content as the Markdown companion (needs allowlist, size/time limits, SSRF protection).")
