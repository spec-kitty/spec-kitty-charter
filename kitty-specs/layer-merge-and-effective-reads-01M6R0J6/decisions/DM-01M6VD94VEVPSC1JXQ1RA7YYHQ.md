# Decision Moment `01M6VD94VEVPSC1JXQ1RA7YYHQ`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `wp15-c21`
- **Input key:** `wp15-c21-registration`
- **Status:** `resolved`
- **Created:** `2026-11-07T16:25:47.118922+00:00`
- **Resolved:** `2026-11-07T16:25:49.447627+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

WP15: is the C21 layer-override gate (a pack holding an id that a pack it builds on holds at a lower level, the id not open) judged at registration in PR A, and with which refusal?

## Options

_(none)_

## Final answer

Yes, through WP14's staged-closure write gate, with its existing refusal 409 governance_policy_violation: holding an id an ancestor holds is a REPLACE of that ancestor's artifact, permitted only where the ancestor's policy opens it (ADR-0063, A15 lock scope, B8). No new code and no contract shape change. WP15's reviewer verifies the staged gate classifies such an id as REPLACE against the ancestor's policy; if it does not, WP15 fixes the gate (red first) rather than adding a 422 code. PR B's merge (IC-07) judges the layer override again on the merged index.

## Rationale

Plan IC-15 lists C17-C21 for the impact check; the contract already carries the gate's 409, and WP14's authorizeStaged already judges REGISTER/REPLACE along the staged closure. A separate 422 would duplicate the gate and widen the contract.

## Change log

- `2026-11-07T16:25:47.118922+00:00` - opened
- `2026-11-07T16:25:49.447627+00:00` - resolved (final_answer="Yes, through WP14's staged-closure write gate, with its existing refusal 409 governance_policy_violation: holding an id an ancestor holds is a REPLACE of that ancestor's artifact, permitted only where the ancestor's policy opens it (ADR-0063, A15 lock scope, B8). No new code and no contract shape change. WP15's reviewer verifies the staged gate classifies such an id as REPLACE against the ancestor's policy; if it does not, WP15 fixes the gate (red first) rather than adding a 422 code. PR B's merge (IC-07) judges the layer override again on the merged index.")
