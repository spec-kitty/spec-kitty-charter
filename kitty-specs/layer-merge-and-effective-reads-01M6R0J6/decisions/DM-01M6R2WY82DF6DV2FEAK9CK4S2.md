# Decision Moment `01M6R2WY82DF6DV2FEAK9CK4S2`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.registration.zip-links`
- **Input key:** `zip_link_detection`
- **Status:** `resolved`
- **Created:** `2026-11-06T09:26:38.082294+00:00`
- **Resolved:** `2026-11-06T09:39:56.657068+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How are links in zip archives detected?

## Options

- java.util.zip plus a small central-directory reader
- Commons Compress zip reader
- Other

## Final answer

Commons Compress zip reader (reports entry types; no hand-written parser)

## Rationale

_(none)_

## Change log

- `2026-11-06T09:26:38.082294+00:00` - opened
- `2026-11-06T09:39:56.657068+00:00` - resolved (final_answer="Commons Compress zip reader (reports entry types; no hand-written parser)")
