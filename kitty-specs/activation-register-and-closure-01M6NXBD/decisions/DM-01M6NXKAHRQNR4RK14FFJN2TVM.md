# Decision Moment `01M6NXKAHRQNR4RK14FFJN2TVM`

- **Mission:** `activation-register-and-closure-01M6NXBD`
- **Origin flow:** `specify`
- **Slot key:** `specify.cascade.lineage-pass`
- **Input key:** `lineage_pass`
- **Status:** `resolved`
- **Created:** `2026-11-05T13:15:31.256472+00:00`
- **Resolved:** `2026-11-05T13:16:35.262286+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

ADR-0059 OQ3: which relations pass through a specializes_from parent, is it transitive, and what if the parent is denied

## Options

- All D2.1 relations, transitive, denied parent stops
- Other

## Final answer

All D2.1 relations plus the enhances pull, transitive through every ancestor; a denied ancestor stops the walk and is reported in skippedDeactivated

## Rationale

_(none)_

## Change log

- `2026-11-05T13:15:31.256472+00:00` - opened
- `2026-11-05T13:16:35.262286+00:00` - resolved (final_answer="All D2.1 relations plus the enhances pull, transitive through every ancestor; a denied ancestor stops the walk and is reported in skippedDeactivated")
