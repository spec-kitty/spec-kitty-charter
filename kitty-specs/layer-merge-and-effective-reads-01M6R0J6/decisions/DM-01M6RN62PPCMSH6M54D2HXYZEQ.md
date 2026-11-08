# Decision Moment `01M6RN62PPCMSH6M54D2HXYZEQ`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.posttasks.client-roundtrip`
- **Input key:** `client_roundtrip`
- **Status:** `resolved`
- **Created:** `2026-11-06T14:46:11.926287+00:00`
- **Resolved:** `2026-11-06T14:46:14.323224+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

Where the FR-042 round trip runs

## Options

_(none)_

## Final answer

Two tests: client-java captures the wire body of a GET then PUT with a JDK HttpServer (no new dependency) and asserts absent lists stay absent or are sent as []; the server side canonicalizes an empty list and an absent member to one stored form (WP03), pinned by an @contract scenario in deployable: PUT of the client's body keeps the ETag and the stored bytes.

## Rationale

_(none)_

## Change log

- `2026-11-06T14:46:11.926287+00:00` - opened
- `2026-11-06T14:46:14.323224+00:00` - resolved (final_answer="Two tests: client-java captures the wire body of a GET then PUT with a JDK HttpServer (no new dependency) and asserts absent lists stay absent or are sent as []; the server side canonicalizes an empty list and an absent member to one stored form (WP03), pinned by an @contract scenario in deployable: PUT of the client's body keeps the ETag and the stored bytes.")
