# Decision Moment `01M6P99GKMK38VC2YVES9T3N5D`

- **Mission:** `whole-pack-closeout-01M6P994`
- **Origin flow:** `specify`
- **Slot key:** `specify.scope.step-markdown-rules`
- **Input key:** `step_markdown_rules`
- **Status:** `resolved`
- **Created:** `2026-11-05T16:39:52.692289+00:00`
- **Resolved:** `2026-11-05T16:39:55.317400+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

#299: move the line-level Markdown rules into the kernel so StepDocument checks them when built, or record the gap as accepted in ADR-0044?

## Options

- kernel move
- ADR-0044 gap record
- Other

## Final answer

kernel move

## Rationale

Operator brief (2026-11-05, parent session) prefers the kernel move if it stays small and keeps MISSIONS_DOMAIN_IS_PURE; grounding found ~10 files, no Gradle or ArchUnit change.

## Change log

- `2026-11-05T16:39:52.692289+00:00` — opened
- `2026-11-05T16:39:55.317400+00:00` — resolved (final_answer="kernel move")
