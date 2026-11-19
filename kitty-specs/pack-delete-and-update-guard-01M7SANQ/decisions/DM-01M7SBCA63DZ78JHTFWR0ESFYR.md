# Decision Moment `01M7SBCA63DZ78JHTFWR0ESFYR`

- **Mission:** `pack-delete-and-update-guard-01M7SANQ`
- **Origin flow:** `specify`
- **Slot key:** `specify.domain.version-order`
- **Input key:** `version_order`
- **Status:** `resolved`
- **Created:** `2026-11-19T07:29:46.691062+00:00`
- **Resolved:** `2026-11-19T07:29:48.707720+00:00`
- **Resolved by:** `operator (relayed by orchestrator session_019C2Tk6DpTSEBeTcn9F6smy)`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

ADR-0065 D2 (ruling B1) makes pack_version a label, not an order. What does the update guard compare, and with which code?

## Options

- PEP 440 precedence, reuse 409 pack_version_conflict
- PEP 440 precedence, new code pack_version_not_newer

## Final answer

Operator acknowledges reversing B1. Newer means PEP 440 public-version precedence through the existing kernel Pep440 ordering. Older-or-equal with other bytes reuses 409 pack_version_conflict with new detail (no new code). Equal with the same bytes is unchanged.

## Rationale

_(none)_

## Change log

- `2026-11-19T07:29:46.691062+00:00` - opened
- `2026-11-19T07:29:48.707720+00:00` - resolved (final_answer="Operator acknowledges reversing B1. Newer means PEP 440 public-version precedence through the existing kernel Pep440 ordering. Older-or-equal with other bytes reuses 409 pack_version_conflict with new detail (no new code). Equal with the same bytes is unchanged.")
