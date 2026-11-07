# Decision Moment `01M6RN677S4XN4WR8ZVMAW777G`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.posttasks.git-tests`
- **Input key:** `git_tests`
- **Status:** `resolved`
- **Created:** `2026-11-06T14:46:16.569614+00:00`
- **Resolved:** `2026-11-06T14:46:18.844044+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

git >= 2.50.1 in tests

## Options

_(none)_

## Final answer

Real-git tests carry the tag git-host, are excluded from check and PIT, and run in a dedicated CI job that asserts git --version >= 2.50.1 first; locally they are reported not run, never passed. check uses a fake git shim (a script on a test PATH) for GitCommand, the version check and NFR-011.

## Rationale

_(none)_

## Change log

- `2026-11-06T14:46:16.569614+00:00` - opened
- `2026-11-06T14:46:18.844044+00:00` - resolved (final_answer="Real-git tests carry the tag git-host, are excluded from check and PIT, and run in a dedicated CI job that asserts git --version >= 2.50.1 first; locally they are reported not run, never passed. check uses a fake git shim (a script on a test PATH) for GitCommand, the version check and NFR-011.")
