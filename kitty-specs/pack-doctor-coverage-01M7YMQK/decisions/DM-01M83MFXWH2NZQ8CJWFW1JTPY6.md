# Decision Moment `01M83MFXWH2NZQ8CJWFW1JTPY6`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `plan`
- **Slot key:** `policy_lock_case_only`
- **Input key:** `policy_lock_case_only`
- **Status:** `resolved`
- **Created:** `2026-11-23T07:21:26.673195+00:00`
- **Resolved:** `2026-11-23T07:21:37.355371+00:00`
- **Resolved by:** `claude`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

Registration case-only check also covers policy-lock URNs; pack-doctor validate checks case-only only for preset items. Fold the policy-lock case-only check into this PR?

## Options

- Follow-up via #636 (Recommended)
- Fold into this PR

## Final answer

Fold into this PR

## Rationale

Operator ruling R-y (2026-11-23), source: operator (AskUserQuestion in this session), answer: "Fold into this PR". pack-doctor's CLOSURE gains the case-only check for policy-lock URNs through the domain factory, so validate answers a case-only policy lock as registration's phase 6 does (pack_reference_artifact_missing, no violation of its own), with a FullPassParityTest row. Added as WP13.

## Change log

- `2026-11-23T07:21:26.673195+00:00` - opened
- `2026-11-23T07:21:37.355371+00:00` - resolved (final_answer="Fold into this PR")
