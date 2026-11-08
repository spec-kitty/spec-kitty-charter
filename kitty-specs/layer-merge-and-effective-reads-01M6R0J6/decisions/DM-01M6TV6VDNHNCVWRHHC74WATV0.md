# Decision Moment `01M6TV6VDNHNCVWRHHC74WATV0`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.phase-a.wp11-contract-calls`
- **Input key:** `wp11_contract_calls`
- **Status:** `resolved`
- **Created:** `2026-11-07T11:09:57.557630+00:00`
- **Resolved:** `2026-11-07T11:10:00.045453+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

WP11 contract calls: Charter-Source-Fingerprint on getPackRegistration's 200, and 400 vs 422 for a malformed packId or PackSource body

## Options

_(none)_

## Final answer

(1) getPackRegistration's 200 carries no Charter-Source-Fingerprint: the registration status is not derived from the pack sources; WP16 adds the status path to the lint rule's exemption list when it serves the operation. (2) A malformed packId or PackSource body answers a synchronous 400 (plain Problem), as contract-delta and WP11's contract say; WP16 T079's '422' is corrected to 400.

## Rationale

Raised by WP11's implementer; keeps the contract and the serving WP consistent.

## Change log

- `2026-11-07T11:09:57.557630+00:00` - opened
- `2026-11-07T11:10:00.045453+00:00` - resolved (final_answer="(1) getPackRegistration's 200 carries no Charter-Source-Fingerprint: the registration status is not derived from the pack sources; WP16 adds the status path to the lint rule's exemption list when it serves the operation. (2) A malformed packId or PackSource body answers a synchronous 400 (plain Problem), as contract-delta and WP11's contract say; WP16 T079's '422' is corrected to 400.")
