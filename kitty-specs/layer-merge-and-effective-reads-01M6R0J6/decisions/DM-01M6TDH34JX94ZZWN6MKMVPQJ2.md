# Decision Moment `01M6TDH34JX94ZZWN6MKMVPQJ2`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.closeout.sync-recipe`
- **Input key:** `sync_recipe`
- **Status:** `resolved`
- **Created:** `2026-11-07T07:10:53.074716+00:00`
- **Resolved:** `2026-11-07T07:11:15.107512+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

The hand-over sync recipe names origin/issue-291-activation-writes (8188c996), which is deleted; main now also carries #403 and #390. How is the A0 branch synced?

## Options

_(none)_

## Final answer

Follow the recipe, with 8188c996 fetched from refs/pull/399/head (its tip c17b57dc is tree-identical to the squash 418fb689). Merge 8188c996 into the A0 head, then git merge-tree --merge-base=8188c996 into origin/main and commit with parents (HEAD, origin/main). #403 and #390 come in as the real delta and are resolved by hand, each resolution recorded.

## Rationale

8188c996 is still reachable; the merge-base trick stays exact because main's content at 418fb689 equals 8188c996 plus one docs commit. No history is rewritten.

## Change log

- `2026-11-07T07:10:53.074716+00:00` - opened
- `2026-11-07T07:11:15.107512+00:00` - resolved (final_answer="Follow the recipe, with 8188c996 fetched from refs/pull/399/head (its tip c17b57dc is tree-identical to the squash 418fb689). Merge 8188c996 into the A0 head, then git merge-tree --merge-base=8188c996 into origin/main and commit with parents (HEAD, origin/main). #403 and #390 come in as the real delta and are resolved by hand, each resolution recorded.")
