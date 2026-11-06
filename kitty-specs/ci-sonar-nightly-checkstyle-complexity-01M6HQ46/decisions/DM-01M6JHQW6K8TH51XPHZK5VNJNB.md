# Decision Moment `01M6JHQW6K8TH51XPHZK5VNJNB`

- **Mission:** `ci-sonar-nightly-checkstyle-complexity-01M6HQ46`
- **Origin flow:** `plan`
- **Slot key:** `plan.register.stale-detection`
- **Input key:** `register_stale_detection`
- **Status:** `resolved`
- **Created:** `2026-11-04T05:50:34.195306+00:00`
- **Resolved:** `2026-11-04T05:53:28.592052+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How is the complexity exception register kept shrink-only (stale entries detected, new methods not exempted)?

## Options

- Method-scoped XPath entries + a Gradle stale-entry check in verifyAll
- Format/Size/sunset guard only; staleness caught at sunset
- CI-only Python script
- Other

## Final answer

XPath per method + stale check: suppress-xpath entries in the one suppressions.xml naming each method; a build-logic Gradle check re-runs Checkstyle (the build's own plugin) over the register's files with the filter off and fails verifyAll on a stale entry or a wrong Size; engineering alignment confirmed

## Rationale

_(none)_

## Change log

- `2026-11-04T05:50:34.195306+00:00` - opened
- `2026-11-04T05:53:28.592052+00:00` - resolved (final_answer="XPath per method + stale check: suppress-xpath entries in the one suppressions.xml naming each method; a build-logic Gradle check re-runs Checkstyle (the build's own plugin) over the register's files with the filter off and fails verifyAll on a stale entry or a wrong Size; engineering alignment confirmed")
