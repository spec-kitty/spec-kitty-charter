# Hand-over to the PR C runner (2026-11-09)

The PR B runner stands down here. This page says where the mission is, what the PR C runner inherits, and what to do
first. The earlier hand-overs (`handover-followups-2026-11-08.md`, `-2026-11-07.md`) still hold for their open items;
`closeout-notes-pr-b.md` holds every PR B review's findings and dispositions.

## Where the mission is

- **PR A0** (#414), **PR A** (#419) and the PR A follow-ups (#423) are merged.
- **PR B** (WP21–WP26) is #427, **merged 2026-11-09** (07:38 UTC). A maintainer landing pass rebased it onto `main` at
  `465e3de2` (which holds #425, the ADR-0066 contracts and the activatable kind `step_enhancement`), squashed each
  fold's red/green pair into one commit (26 commits; the rewrite dropped the commit signatures, which `main` does not
  require), and folded one second-opinion finding: a step contest whose `overrides` entries named two different
  siblings was settled by closure order; it now refuses `pack_sibling_conflict` (C15).
- **WP27–WP29** (PR C) are planned: lanes `lane-y` (WP27), `lane-z` (WP28) and `lane-{` (WP29). They were allocated
  before PR B; rebuild each on `main` once #427 is merged (below).

## First steps for the PR C runner

1. **#427 is merged.** Rebase the planning branch (`issue-10-layer-merge-effective-reads`) onto `main`
   (the operator prefers rebase to merge). Its commits beyond `main` are planning-only; check that `main` already
   holds everything else before replaying.
2. **Re-pin and re-record.** After the rebase, `finalize-tasks --refresh-planning-commit --allow-orphaned` re-points
   the recorded planning commit. If it refuses on `owned_files` naming retired files, drop those entries (as was done
   for WP01 and WP11). Any edit to `spec.md`, `plan.md`, `tasks.md` or a WP file makes the analysis stale: re-record
   with `agent mission record-analysis --report-only` and do NOT amend its commit (its receipt pins the SHA; decision
   `01M6XSRSK0N9PEVJ7EHM45475V`).
3. **Rebuild each lane on `main`.** `agent action implement` cuts the lane from the old mission branch and its merge of
   the planning commit conflicts. Abort that merge and make one signed merge commit (`git commit-tree -S`) whose tree
   is the dependency lanes' code tree (or `main`'s, once PR B is merged) with the lane's own status files, recording
   the dependency lane tips and the planning commit as parents, so the CLI's dependency merge is a no-op. Then run
   `implement` again.
4. **Run claims yourself and re-sign before work is stacked.** Claim with the full identity
   (`--agent claude:sonnet:java-jenny`; reviewers `claude:opus:reviewer-renata`). The CLI commits status unsigned; re-sign
   them before an implementer stacks work on them. A CLI commit beneath a lane head that a status event records stays
   unsigned (decision `01M6XY0SVNASDDCNTJ3DPJP3Q6`); PR history is rebuilt from signed commits anyway.
5. **Keep lanes level.** The move to `for_review` refuses a lane whose planning files differ from the recorded planning
   commit: re-pin and merge the new planning commit into the lane when the planning branch gains research or decision
   files. The approval refuses on a missing issue-matrix row for any issue the planning files mention: record verdicts
   (`agent issue-verdict`) and mirror the matrix into the planning branch and the lane.

## What PR C inherits from PR B

- **The suggestion rule** (a `not_selected` artifact reached only through `suggests`; `enhances` is not a hard relation;
  decision `01M6Y6VE6VZQ4MXD98N93JB3GJ`) lives in `activations.domain` (`EffectiveSet`). The effective action context
  must reuse it, or move it behind the port into `packs`, not write a second copy (squad A7).
- **Steps under a winning mission type:** a winning `mission_type:M` does not take its losers' unique steps, so a
  built-in `M/s2` survives under a project `M` (A7). Confirm this reading with the architect for the action context.
- **Bindings:** only mission step contracts bind to their mission type (`PackCompositionFacts.bindings`, fed since
  WP25). `MergedSnapshot.missionTypes` and `stepBinding` (step enhancers) are PR C's.
- **IC-13** (the deny-satisfied-by-override write rule, WP29) is PR C; the PR B fragments say so.
- **Tags:** the graph and context reads use `EffectiveTag` over the closure-scoped fingerprint
  (`ClosureSnapshot.closureFingerprint()`), per representation; `EffectiveETag.yaml` already names them.
- **A1 floor** (operator decision `01M6ZCXAJ9MTTF9XJSR89F3NYG`): the union across levels of the copy in force at each
  level; within a level the replacing copy wins. One rule for reads, writes and notices (`LayerMerge.floorOf`).

## Open follow-ups (not PR C's unless the operator schedules them)

- #429 (from the #427 landing): `ClosureViewSession` filters `action:` URNs through `activatable()`, so a step
  enhancer's targets come back `[]` in `enhancedTargetNotInForce` and a pruned required or locked step is left out of
  the skipped report. Latent until step enhancements are stored (#428); the first item needs a contract change
  (`EnhancerWithoutTarget.targets` admitting `action`). PR C's graph and action-context reads touch the same
  boundary: read #429 before WP27/WP28, and check #425's `step_enhancement` kind in the in-force resolution.

- #426: the PR B pre-PR squad's deferred items (`activationsAffected` under the registry lock against NFR-009; If-Match
  tag from the write's own session; registration not trying the merge; two preset winner functions; static test
  counters; enum mapping by name; the `304` cost; test nits).
- #416 (lookup cache), #417 (notice kinds; do not widen without the operator), #418's remaining items, #358 (only the
  distribution pack may settle a sibling contest; the same-level settlement gap, DIV-219).
- Do not take on #406, #407, #409, #411, #97 or #99.

## Rulings and standing rules still in force

- Test first, red before green; never skip, disable or weaken a test; no empty commit to re-trigger CI.
- Implementers on sonnet (`java-jenny`), reviewers on opus (`reviewer-renata`); adversarial squads at the plan point and
  before each PR, every finding dispositioned (fixed in its own commit, or deferred with a rationale and an issue).
- The gate is `verifyAll` with `-PopenPacksDir=<open-packs at openPacksRef>`; it need only pass on the final PR state.
  No per-target-environment builds, no timing checks; `:packs:wholePackRoundTrip` at closeout only.
- The contract is 0.x: fragments under `contracts/charter/changes`, one `###` block per file, `### Breaking` where a
  documented response of an operation on `main` changes (a new member on a closed response counts). Release hold #45.

## Working notes from PR B

- **Run the whole gate early on the rebuilt branch.** The per-WP gates ran module checks with `-PcontractBaseline=none`;
  `verifyAll` also runs `client-java` and the contract compatibility check, which caught a stale client fixture and a
  description test pinning old wording. The compatibility check needs full history: unshallow the clone first
  (`git fetch --unshallow --tags origin`).
- **Docs and code folds in parallel** worked well in two worktrees (`.worktrees/pr-b`, `.worktrees/pr-b-docs`), with the
  docs commits cherry-picked onto the PR branch and a final wording reconciliation against the landed code.
- **Container restarts** keep the venv (`~/.venv-sk`), `/home/user/open-packs` and the worktrees; re-check for unpushed
  CLI commits after one. A finished sub-agent cannot be resumed after a restart; start a fresh one with the previous
  cycle's review file.
