# Decision Moment `01M7SBCTZ9198XCJAYBT57H10M`

- **Mission:** `pack-delete-and-update-guard-01M7SANQ`
- **Origin flow:** `specify`
- **Slot key:** `specify.wire.delete-refusal`
- **Input key:** `delete_refusal_code`
- **Status:** `resolved`
- **Created:** `2026-11-19T07:30:03.881131+00:00`
- **Resolved:** `2026-11-19T07:30:06.081250+00:00`
- **Resolved by:** `operator (relayed by orchestrator session_019C2Tk6DpTSEBeTcn9F6smy)`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Which refusal does a DELETE with dependents answer?

## Options

- new 409 pack_has_dependents naming each dependent
- reuse artifact_referenced

## Final answer

New 409 pack_has_dependents whose violations name each dependent pack, activation and artifact referrer.

## Rationale

_(none)_

## Change log

- `2026-11-19T07:30:03.881131+00:00` - opened
- `2026-11-19T07:30:06.081250+00:00` - resolved (final_answer="New 409 pack_has_dependents whose violations name each dependent pack, activation and artifact referrer.")
