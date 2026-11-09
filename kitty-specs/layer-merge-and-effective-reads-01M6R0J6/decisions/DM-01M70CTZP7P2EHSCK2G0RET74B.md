# Decision Moment `01M70CTZP7P2EHSCK2G0RET74B`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `pr-c-context-suggestion-scope`
- **Input key:** `pr-c-context-suggestion-scope`
- **Status:** `resolved`
- **Created:** `2026-11-09T14:54:15.239065+00:00`
- **Resolved:** `2026-11-09T14:54:17.157188+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

The context judges 'reached only through suggests' within the walk; the listing judges it over every artifact in force. Which scope does the action context use (one definition, 01M6Y6VE6VZQ4MXD98N93JB3GJ)?

## Options

- the listing rule: a context suggestion is an artifact the listing gives a suggestion, reached through suggests from a listed context entry (so context suggestions are a subset of listing suggestions)
- context-local hard reach

## Final answer

the listing rule: a context suggestion is an artifact the listing gives a suggestion, reached through suggests from a listed context entry (so context suggestions are a subset of listing suggestions)

## Rationale

Runner after the WP28 review (MINOR-2): decision 01M6Y6VE6VZQ4MXD98N93JB3GJ rules one suggestion definition for the listing and the context. An artifact that some in-force artifact reaches through a hard relation is no suggestion anywhere, so the context may not call it one. The edge data (when, reason) is the suggests edge from the context entry (ADR-0018 D3 tie rule).

## Change log

- `2026-11-09T14:54:15.239065+00:00` - opened
- `2026-11-09T14:54:17.157188+00:00` - resolved (final_answer="the listing rule: a context suggestion is an artifact the listing gives a suggestion, reached through suggests from a listed context entry (so context suggestions are a subset of listing suggestions)")
