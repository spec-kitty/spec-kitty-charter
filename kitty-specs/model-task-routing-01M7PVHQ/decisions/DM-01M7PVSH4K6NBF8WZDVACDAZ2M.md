# Decision Moment `01M7PVSH4K6NBF8WZDVACDAZ2M`

- **Mission:** `model-task-routing-01M7PVHQ`
- **Origin flow:** `specify`
- **Slot key:** `specify.adr.step-tier`
- **Input key:** `step_tier`
- **Status:** `resolved`
- **Created:** `2026-11-18T08:18:53.715835+00:00`
- **Resolved:** `2026-11-18T08:33:44.237070+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

ADR-0080 d12: steps gain optional model_tier (a cost tier); request modelTier wins over the step; the effective tier caps the catalog pick like tier_constraints?

## Options

- Caps the catalog pick (recommended)
- Reported only, never changes a candidate (core behaviour)
- Other

## Final answer

Tiers advise and never hide a model: report the effective tier (source step or request, with the offer); catalog candidates are the top-ranked inside every tier in play and, when different, the top-ranked overall, each flagged withinTierConstraint and withinModelTier; tier_constraints annotates too, it does not filter (operator 2026-11-18: the client may always override)

## Rationale

_(none)_

## Change log

- `2026-11-18T08:18:53.715835+00:00` - opened
- `2026-11-18T08:33:44.237070+00:00` - resolved (final_answer="Tiers advise and never hide a model: report the effective tier (source step or request, with the offer); catalog candidates are the top-ranked inside every tier in play and, when different, the top-ranked overall, each flagged withinTierConstraint and withinModelTier; tier_constraints annotates too, it does not filter (operator 2026-11-18: the client may always override)")
