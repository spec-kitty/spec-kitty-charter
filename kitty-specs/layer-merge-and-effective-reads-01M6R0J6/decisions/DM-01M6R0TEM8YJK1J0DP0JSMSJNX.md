# Decision Moment `01M6R0TEM8YJK1J0DP0JSMSJNX`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `specify`
- **Slot key:** `specify.registration.delete_reach`
- **Input key:** `delete_reach`
- **Status:** `resolved`
- **Created:** `2026-11-06T08:50:19.400083+00:00`
- **Resolved:** `2026-11-06T08:59:21.705850+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What does a per-artifact DELETE on a pack do to references?

## Options

- Drop its outbound edges and preset items; refuse inbound references from other packs
- Refuse while anything references it
- Allow; the compose refuses later
- Other

## Final answer

Drop its outbound edges and preset items in the same pack; refuse inbound references from other packs (409); add DELETE for single relationships

## Rationale

_(none)_

## Change log

- `2026-11-06T08:50:19.400083+00:00` - opened
- `2026-11-06T08:59:21.705850+00:00` - resolved (final_answer="Drop its outbound edges and preset items in the same pack; refuse inbound references from other packs (409); add DELETE for single relationships")
