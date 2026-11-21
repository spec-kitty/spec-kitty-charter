# Decision Moment `01M7TS640K3TT3RXYDCS7NSWZW`

- **Mission:** `closure-checks-behind-ports-01M7TS43`
- **Origin flow:** `specify`
- **Slot key:** `specify.scope.parity`
- **Input key:** `parity_test`
- **Status:** `resolved`
- **Created:** `2026-11-19T20:50:18.259354+00:00`
- **Resolved:** `2026-11-19T21:10:46.459913+00:00`
- **Resolved by:** `operator (relayed by orchestrator session_019C2Tk6DpTSEBeTcn9F6smy)`
- **Opened by:** `claude::java-jenny`
- **Other answer:** `false`

## Question

D6: The parity test, given ADR-0070 (charter tests read no open-packs content) and fixtures at tests/fixtures/invalid/?

## Options

- (a) charter-owned mirror packs, each with an open-packs-format expected-code file, plus cases for the collected codes and their order; open-packs charter_service_check.py stays the real gate
- (b) read open-packs at its pinned commit in a test
- (c) drop: open-packs charter_service_check.py already is the parity test

## Final answer

(c) Operator 2026-11-19: drop the parity test; open-packs' charter_service_check.py already delivers it. Note this in the spec and on the issue.

## Rationale

_(none)_

## Change log

- `2026-11-19T20:50:18.259354+00:00` - opened
- `2026-11-19T21:10:46.459913+00:00` - resolved (final_answer="(c) Operator 2026-11-19: drop the parity test; open-packs' charter_service_check.py already delivers it. Note this in the spec and on the issue.")
