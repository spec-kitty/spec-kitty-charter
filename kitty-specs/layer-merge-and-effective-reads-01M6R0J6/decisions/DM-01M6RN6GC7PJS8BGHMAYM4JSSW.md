# Decision Moment `01M6RN6GC7PJS8BGHMAYM4JSSW`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.posttasks.red-evidence`
- **Input key:** `red_evidence`
- **Status:** `resolved`
- **Created:** `2026-11-06T14:46:25.927857+00:00`
- **Resolved:** `2026-11-06T14:46:28.215663+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

What counts as red-first evidence for a contract scenario

## Options

_(none)_

## Final answer

Only an assertion failure with every step bound (a wrong status or body). Undefined or pending steps are not red: a suite guard fails the run on any UNDEFINED or PENDING step. Contract WPs commit their scenarios tagged @WIP (excluded); the serving WP binds the glue, records the red assertion failure, then removes @WIP.

## Rationale

_(none)_

## Change log

- `2026-11-06T14:46:25.927857+00:00` - opened
- `2026-11-06T14:46:28.215663+00:00` - resolved (final_answer="Only an assertion failure with every step bound (a wrong status or body). Undefined or pending steps are not red: a suite guard fails the run on any UNDEFINED or PENDING step. Contract WPs commit their scenarios tagged @WIP (excluded); the serving WP binds the glue, records the red assertion failure, then removes @WIP.")
