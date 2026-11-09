# PR C working notes (runner, 2026-11-09)

Kept so another runner or session can take over. The live state is on the branches; these are the notes behind it.

## State at this snapshot (updated)

- **WP27** approved (two review cycles), lane `kitty/mission-layer-merge-and-effective-reads-01M6R0J6-lane-y`.
- **WP28** approved (two review cycles), lane `…-lane-z` (built on `lane-y`).
- **WP29** implemented on lane `…-lane-{` (built on `lane-z`): T143–T146 and T165 marked done, six signed commits
  `66481b02..0fa69ecc`, pushed; hand-off `wp29-handoff.md`. **Not yet reviewed.** Next: re-pin the planning commit,
  merge it into `lane-{`, `move-task WP29 --to for_review --allow-sparse-checkout` (decision `01M70E6PK2HSWEFDVS9KGRGE71`:
  the CLI's lane-id pattern rejects `{`), then an opus `reviewer-renata` review.
- **The `{` in the lane directory breaks the build:** `:charter-api:validateContract` fails with "Illegal character in
  path", so nothing that compiles the API runs inside `.worktrees/…-lane-{`. The WP29 implementer built and tested
  from a copy (`/tmp/claude-0/wp29-build`, synced by a script) and committed only in the lane. A reviewer must do the
  same (or check out the lane head into a worktree whose path has no `{`). The PR C history rebuild and the
  `verifyAll` gate run on a normally named branch/worktree, so the gate is unaffected.
- Two NITs per approved WP (Javadoc/comment reflow, long description lines) are folded at closeout.
- The coordination worktree carries ~380 untracked files (copies of main-era files); nothing tracked is modified and
  nothing of it is committed. Ignore them, or `git clean` that worktree only after checking.

## Files

- `wp27-handoff.md`, `wp28-handoff.md`: the implementers' hand-offs (public signatures, red-first evidence, notes for
  the next WP). WP29's brief should read both: the docs and divergence items are listed there.
- `wp27-review-cycle-*.md`, `wp28-review-cycle-1.md`: the reviews and the runner's dispositions.
- `resign.sh.txt`: the script that re-signs the CLI's unsigned status commits on the mission branch (and a lane head
  above them) before a push. The two `record-analysis` commits on the planning branch stay unsigned on purpose
  (decision `01M6XSRSK0N9PEVJ7EHM45475V`: the analysis receipt pins their SHAs).
- `../plan-point-squad-pr-c/`: the plan-point squad's reports and dispositions.

## Open with the operator

- #439: the smallest-URN tie of `satisfiedBy`/`overriddenBy` (needs a ruling; not PR C's).
- A7 dissent from the plan-point architect (a losing mission type's unique steps survive); kept as built.

## Coordination

Phase 2 of the step-enhancements mission (#438, runner `session_01JuXv6fZ4jMGna4kLBUA2jr`) consumes `StepHolding`,
`NodeReference`/`NodeRef`, the walk and `ACTION_NOT_FOUND`. WP27's signatures were sent; send WP28's `NodeRef` and
`NodeReference` after approval, and the PR C merge notice on #438.
