# Decision Moment `01M7SAVME8G11194RPTV7EX95G`

- **Mission:** `pack-check-cli-01M7SASC`
- **Origin flow:** `specify`
- **Slot key:** `specify.release.version`
- **Input key:** `release_version`
- **Status:** `resolved`
- **Created:** `2026-11-19T07:20:40.136753+00:00`
- **Resolved:** `2026-11-19T07:48:27.164235+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Which service release carries pack-check first?

## Options

- service-v0.1.0-rc.2, via a separate release PR after this lands
- service-v0.2.0-rc.1 (minor: new capability)
- Other

## Final answer

A: service-v0.1.0-rc.2 through a small release PR after the mission; tell the orchestrator the tag and commit when the native run is green (operator via orchestrator, 2026-11-19)

## Rationale

_(none)_

## Change log

- `2026-11-19T07:20:40.136753+00:00` - opened
- `2026-11-19T07:48:27.164235+00:00` - resolved (final_answer="A: service-v0.1.0-rc.2 through a small release PR after the mission; tell the orchestrator the tag and commit when the native run is green (operator via orchestrator, 2026-11-19)")
