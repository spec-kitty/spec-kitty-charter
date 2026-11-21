# Decision Moment `01M7TS5ZN2B9G0GCXKSBQHBXC1`

- **Mission:** `closure-checks-behind-ports-01M7TS43`
- **Origin flow:** `specify`
- **Slot key:** `specify.design.notices`
- **Input key:** `impact_notices`
- **Status:** `resolved`
- **Created:** `2026-11-19T20:50:13.794157+00:00`
- **Resolved:** `2026-11-19T21:10:41.994165+00:00`
- **Resolved by:** `operator (relayed by orchestrator session_019C2Tk6DpTSEBeTcn9F6smy)`
- **Opened by:** `claude::java-jenny`
- **Other answer:** `false`

## Question

D4: ImpactCheck is a kernel Rule, but the impact also yields notices (floor/lock/activations). Shape?

## Options

- (a) one pass: the port returns Impact(verdict Rationale, notices) and exposes the Rule view; one compose per closure
- (b) Rule only; notices from a second compose

## Final answer

(a) Operator 2026-11-19: one pass; the port returns the verdict and the notices and exposes the kernel Rule view.

## Rationale

_(none)_

## Change log

- `2026-11-19T20:50:13.794157+00:00` - opened
- `2026-11-19T21:10:41.994165+00:00` - resolved (final_answer="(a) Operator 2026-11-19: one pass; the port returns the verdict and the notices and exposes the kernel Rule view.")
