# Decision Moment `01M6HQ9K8ZPB1W0PS5CNDEE9WR`

- **Mission:** `ci-sonar-nightly-checkstyle-complexity-01M6HQ46`
- **Origin flow:** `specify`
- **Slot key:** `specify.complexity.limits`
- **Input key:** `complexity_limits`
- **Status:** `resolved`
- **Created:** `2026-11-03T22:08:23.327167+00:00`
- **Resolved:** `2026-11-04T05:44:40.751511+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Which Checkstyle complexity limits should gate each pull request?

## Options

- Core-aligned limits + shrink-only register
- Ceiling just above today's worst (near-zero entries)
- Strict norms (CC 10, NPath 200, 50 lines) + large register
- Other

## Final answer

Core-aligned limits + shrink-only register: CyclomaticComplexity 15, NPathComplexity 1000, MethodLength 60, NestedIfDepth 1, on main and test; existing violators as shrink-only suppressions with a reason each

## Rationale

_(none)_

## Change log

- `2026-11-03T22:08:23.327167+00:00` - opened
- `2026-11-04T05:44:40.751511+00:00` - resolved (final_answer="Core-aligned limits + shrink-only register: CyclomaticComplexity 15, NPathComplexity 1000, MethodLength 60, NestedIfDepth 1, on main and test; existing violators as shrink-only suppressions with a reason each")
