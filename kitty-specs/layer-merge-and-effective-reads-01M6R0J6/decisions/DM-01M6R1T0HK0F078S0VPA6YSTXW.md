# Decision Moment `01M6R1T0HK0F078S0VPA6YSTXW`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.registration.git-client`
- **Input key:** `git_client`
- **Status:** `resolved`
- **Created:** `2026-11-06T09:07:33.555823+00:00`
- **Resolved:** `2026-11-06T09:07:35.833586+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How does POST /packs fetch a git source?

## Options

- JGit library
- system git binary required on the host
- Other

## Final answer

System git binary is a host requirement (no JGit); documented in the docs and the repository README.md

## Rationale

_(none)_

## Change log

- `2026-11-06T09:07:33.555823+00:00` - opened
- `2026-11-06T09:07:35.833586+00:00` - resolved (final_answer="System git binary is a host requirement (no JGit); documented in the docs and the repository README.md")
