# Decision Moment `01M70E6PK2HSWEFDVS9KGRGE71`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `pr-c-lane-brace-sparse`
- **Input key:** `pr-c-lane-brace-sparse`
- **Status:** `resolved`
- **Created:** `2026-11-09T15:18:07.714060+00:00`
- **Resolved:** `2026-11-09T15:18:09.780871+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

implement WP29 refuses lane-{ as legacy sparse-checkout state: the CLI's lane-id pattern (lane-[a-z]+, branch_naming.py:109) does not match the allocated lane id lane-{, so its own managed status exclusion reads as unmanaged. Proceed?

## Options

- proceed with --allow-sparse-checkout: the sparse config is byte-identical to the managed one on lane-y and lane-z
- re-allocate WP29 to another lane

## Final answer

proceed with --allow-sparse-checkout: the sparse config is byte-identical to the managed one on lane-y and lane-z

## Rationale

Runner: lane-{ worktree's config.worktree and info/sparse-checkout equal lane-y's and lane-z's (sparseCheckout=true, cone=false, /* minus the two Mission status files), which the CLI wrote itself; only the lane-id regex rejects '{'. Re-allocating would rewrite lanes.json for a CLI naming bug. No core change or issue (C-003); noted in the closeout notes. Later CLI steps on lane-{ are watched for the same pattern.

## Change log

- `2026-11-09T15:18:07.714060+00:00` - opened
- `2026-11-09T15:18:09.780871+00:00` - resolved (final_answer="proceed with --allow-sparse-checkout: the sparse config is byte-identical to the managed one on lane-y and lane-z")
