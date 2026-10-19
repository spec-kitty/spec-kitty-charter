# Decision Moment `01M57WH7P0QZWHKYGBA2R0MA3K`

- **Mission:** `charter-contract-and-build-retro-01M57QHJ`
- **Origin flow:** `plan`
- **Slot key:** `plan.contract.own-lint`
- **Input key:** `own_contract_lint`
- **Status:** `resolved`
- **Created:** `2026-10-18T16:11:50.336777+00:00`
- **Resolved:** `2026-10-18T16:11:53.687888+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What does the charter's own contract linting run on?

## Options

_(none)_

## Final answer

The charter owns its contract checks (operator 2026-10-18, superseding DM 01M57VVQG0E6CRAPEH597Q2VNY): a ruleset the charter authors in contracts/lint/ (its own conventions, not a copy of core's), run by a pinned, checksum-verified vacuum binary through a Gradle task inside verifyAll, with planted-fixture tests per rule. The goal is to redesign the charter offering; core's rough edges are handled by the integration/ACL seam, and core changes later.

## Rationale

_(none)_

## Change log

- `2026-10-18T16:11:50.336777+00:00` — opened
- `2026-10-18T16:11:53.687888+00:00` — resolved (final_answer="The charter owns its contract checks (operator 2026-10-18, superseding DM 01M57VVQG0E6CRAPEH597Q2VNY): a ruleset the charter authors in contracts/lint/ (its own conventions, not a copy of core's), run by a pinned, checksum-verified vacuum binary through a Gradle task inside verifyAll, with planted-fixture tests per rule. The goal is to redesign the charter offering; core's rough edges are handled by the integration/ACL seam, and core changes later.")
