# Tooling friction: adr-0066-step-enhancement-service-alignment-01M6ZRYK

Recorded for the Spec Kitty maintainers. None of this changed what was built.

- 2026-11-09 setup: `uv tool install --force -e ../spec-kitty` was refused by the session's permission classifier; the
  run used the environment's editable install from `/opt/spec-kitty` at the same commit (dispatcher ruling).
- 2026-11-09 finalize-tasks: refused with `LANE_MEMBERSHIP_FROZEN` / `status_unreadable` until the coordination branch
  existed locally; `git branch <coord> origin/<coord>` then `spec-kitty doctor coordination --fix` materialized it.
- 2026-11-09 finalize-tasks: committed tasks.md, the WP prompts and lanes.json, but not the plan, quickstart or
  contract-draft edits made in the same fold; those were committed by hand. Its commit carries no attribution
  trailer (left as is, dispatcher ruling).
- 2026-11-09 next: `spec-kitty next` is blocked because the status log still holds WP07–WP11 as `planned` after the
  operator's split moved their prompts to the phase-2 mission; `move-task WP07 --to canceled` refuses because the WP
  file is gone. The event log was not hand-edited; the loop is driven with `agent action implement WPxx`. Listed in
  the PR's Deferred section.
- 2026-11-09 WP01 claim: lane-a was cut from the stale mission branch (`stale lane base` warning) and `--base` cannot
  re-parent an existing lane. The lane held `main` and every code commit of the target, missing only three
  mission-doc commits, so it was reused; the implementer read the current prompt from the main checkout and the
  reviewer checks the diff against the current target tip.
- 2026-11-09 lanes: a new lane worktree has no synced packs, and `-PopenPacksDir=../spec-kitty-open-packs` resolves
  under `.worktrees/`; `syncPacks` was run in each lane with the absolute open-packs path.
- 2026-11-09 WP02: `move-task --to for_review` refused because the lane carried `kitty-specs/_staging/...` files from
  the mission branch it was cut through (the target had since moved them); removed on the lane, as the CLI advised.
- 2026-11-09 WP02: approval required issue-matrix rows for every number cited anywhere in the mission, context-only
  citations included (#10, #11, #99, #175 as `open-packs#175`, #425, #429–#435, #438); recorded as `not-applicable`
  with a reason, #428 and #437 `in-mission`, #424 `deferred-with-followup` (#438).
- 2026-11-09 PR C merged (main 55c7d824): the CLI has no command to bring a lane-based mission onto a new main, so
  `origin/main` was merged into lane-d at the WP04 boundary by hand (two text conflicts, both sides' meaning kept),
  and WP04 was reviewed on the merged state. The target branch (planning docs only) is rebased at closeout, after
  the lanes are merged, so live lanes never see rewritten target history.
- 2026-11-09 WP04: `DomainRoundTrip` (WP01's harness, not a listed edit of WP04) wrote graph shards after its
  read-back, which the new edge rules then judged without a graph; the run lead approved the harness-order fix
  (scaffold only, no expectation change) as a listed edit.
- 2026-11-09 signing: commits the CLI makes itself (finalize-tasks "Add tasks for mission …", `agent action implement`
  "WPxx claimed for implementation", record-analysis, requirement mapping) carry no SSH signature, so GitHub shows
  them as Unverified; 14 such commits are on the target branch, some from before this session. They are re-signed by
  the closeout rebase onto main rather than by rewriting pushed history while lanes are live.
