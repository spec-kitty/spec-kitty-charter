# Decision Moment `01M7FARH7YMGS2X0BRV1HKH47B`

- **Mission:** `pack-closure-rulesets-01M7FA90`
- **Origin flow:** `specify`
- **Slot key:** `specify.wire.unmodelled-file`
- **Input key:** `unmodelled_file_code`
- **Status:** `resolved`
- **Created:** `2026-11-15T10:06:34.238458+00:00`
- **Resolved:** `2026-11-15T11:21:34.164459+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

Decision 5 says an unmodelled file is file_not_held at WARNING, but today registration refuses it with violation 'unmodelled' (pinned in scenarios) and #554 says wire codes do not change. Keep both typed codes with the wire unchanged (a), or apply decision 5 literally: an unmodelled file becomes a file_not_held warning and is accepted (b, breaking)?

## Options

- (a) keep unmodelled as a violation and file_not_held as the excluded-file warning, both typed; wire unchanged
- (b) unmodelled file becomes file_not_held WARNING and is accepted; breaking
- Other

## Final answer

Warning (operator, 2026-11-15): an unmodelled file is a file_not_held WARNING. Pack authors may add docs and reference files that do not affect the service: warn, then ignore. The file is not held and not round-tripped. Registration no longer refuses it (wire change: the 'unmodelled' violation goes).

## Rationale

_(none)_

## Change log

- `2026-11-15T10:06:34.238458+00:00` - opened
- `2026-11-15T11:21:34.164459+00:00` - resolved (final_answer="Warning (operator, 2026-11-15): an unmodelled file is a file_not_held WARNING. Pack authors may add docs and reference files that do not affect the service: warn, then ignore. The file is not held and not round-tripped. Registration no longer refuses it (wire change: the 'unmodelled' violation goes).")
