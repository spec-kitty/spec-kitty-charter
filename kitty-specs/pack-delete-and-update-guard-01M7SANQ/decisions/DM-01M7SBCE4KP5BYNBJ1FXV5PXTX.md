# Decision Moment `01M7SBCE4KP5BYNBJ1FXV5PXTX`

- **Mission:** `pack-delete-and-update-guard-01M7SANQ`
- **Origin flow:** `specify`
- **Slot key:** `specify.wire.from-to`
- **Input key:** `from_to_declaration`
- **Status:** `resolved`
- **Created:** `2026-11-19T07:29:50.739392+00:00`
- **Resolved:** `2026-11-19T07:29:52.965680+00:00`
- **Resolved by:** `operator (relayed by orchestrator session_019C2Tk6DpTSEBeTcn9F6smy)`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How does a replacement declare the version it moves from and to?

## Options

- (a) report previousVersion and packVersion; If-Match is the from-guard
- (b) new Charter-Pack-From-Version header

## Final answer

(a) The answer reports previousVersion and packVersion; If-Match stays the from-guard. No new request header.

## Rationale

_(none)_

## Change log

- `2026-11-19T07:29:50.739392+00:00` - opened
- `2026-11-19T07:29:52.965680+00:00` - resolved (final_answer="(a) The answer reports previousVersion and packVersion; If-Match stays the from-guard. No new request header.")
