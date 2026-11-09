# Decision Moment `01M6XY0SVNASDDCNTJ3DPJP3Q6`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `cli-commits-under-lane-head`
- **Input key:** `cli-commits-under-lane-head`
- **Status:** `resolved`
- **Created:** `2026-11-08T15:56:48.373851+00:00`
- **Resolved:** `2026-11-08T15:56:50.885330+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

CLI status commits that end up beneath a lane head recorded in a status event (e.g. WP21 for_review lane_head f0a000e7 above 3f09bc51 and 2edd3ccc) cannot be re-signed without invalidating the recorded head. Re-sign anyway, or leave them unsigned?

## Options

- re-sign (breaks recorded lane_head)
- leave them unsigned; runner re-signs CLI commits before work is stacked on them where it can

## Final answer

leave them unsigned; runner re-signs CLI commits before work is stacked on them where it can

## Rationale

Runner, 2026-11-08: status events record lane_head SHAs, so rewriting history beneath them would make the event log name commits that no branch holds. Lane and mission-branch history does not land on main: PR B is rebuilt as one signed commit per WP. The runner now runs re-claims itself and re-signs the CLI commits before an implementer stacks work on them. Same reasoning as 01M6XSRSK0N9PEVJ7EHM45475V.

## Change log

- `2026-11-08T15:56:48.373851+00:00` - opened
- `2026-11-08T15:56:50.885330+00:00` - resolved (final_answer="leave them unsigned; runner re-signs CLI commits before work is stacked on them where it can")
