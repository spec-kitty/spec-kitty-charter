# Hand-over to the PR B/C runner (2026-11-08)

The PR A runner stands down here. This page says where the mission is, what the next runner inherits, and what to do
first. The earlier hand-over (`handover-followups-2026-11-07.md`) still holds for its open items.

## Where the mission is

- **PR A0** (WP01–WP09, WP30) is merged as #414.
- **PR A** (WP10–WP20) is #419, branch `issue-10-pr-a-registration`, rebuilt on `main` at `6427f515`, ready for review.
  Its handover comment on the PR lists the CI state, the open decisions and the follow-ups. CI was red only from a
  github.com outage (release downloads answering 504); the PR also moves the PlantUML diagram and external-link checks
  to a nightly workflow (`docs-nightly.yml`, operator request 2026-11-08).
- **Squad follow-ups** of PR A are #418; the PR A closeout notes (`closeout-notes-pr-a.md`) record every finding and its
  disposition.
- **WP21–WP29** (PR B: WP21–WP26; PR C: WP27–WP29) are planned. Their lanes (`lane-s` to `lane-{`) were allocated
  against the pre-rebuild history; rebuild each lane on `main` once #419 is merged (below).

## First steps for the B/C runner

1. **Wait for #419 to merge**, then sync the planning branch (`issue-10-layer-merge-effective-reads`) with `main` and
   rebuild the B lanes on `main`. The CLI's dependency merge conflicts with the lanes' pre-rebuild A0/A history; the
   PR A runner rebuilt each new lane as one signed merge commit whose tree is the dependency lanes' code tree with the
   lane's own `kitty-specs/` kept, then ran `implement` again. The same works for B.
2. **Re-record the analysis** if `implement` refuses with `analysis_report_required`
   (`spec-kitty agent mission record-analysis --input-file …`; it commits on its own, so amend with `-S` and the trailer).
3. **Claim with the full identity.** Run `agent action implement WPxx --agent claude:<model>:<profile>`. A bare
   `--agent claude` records no model or profile, and the approval later refuses as "not provably another identity";
   the only way past that is `--force`, which needs the operator. WP17 and WP19 needed it for exactly this reason.
4. **Keep each lane's `kitty-specs/` level with the planning branch** before `move-task --to approved`; the approval
   refuses a lane whose planning files differ. Mirror every `issue-verdict` (which commits to the mission branch) into
   the planning branch, or the approval refuses on a missing issue-matrix row.

## Must-do items for PR B

- **Per-pack write generation key (#418).** Per-artifact commits and removals bracket `PackWriteGeneration.ofPack`
  with `pack.value()` (possibly the declared `pack_id`); whole-pack swaps use the configured name. Nothing reads
  `ofPack` in PR A. Fix it before any facts cache keys on it.
- **WP25 owns `ActivationImpactAdapter`.** PR A created it with `activationRoots()` only (WP15, out-of-map). WP25 adds
  the affected-activations notice. `impact.committed(...)` is not called for per-artifact writes or removals in PR A;
  the notices are PR B's.
- **Notice kinds (#417).** Notices name activatable kinds only; other kinds are dropped and logged at WARN (operator
  ruling, decision `01M6VXMCDQJWBJXGRQNMF5G6SV`). Do not widen them in PR B unless the operator schedules #417.
- **PR B description note (from the WP15 review).** Create and replace now refuse wrong-type scalars and duplicate
  members with `422`; they were coerced or last-wins before.
- **Lookup cost (#416).** PR A keeps local fixes (WP15's `OfferingReadService.pinned()`, WP16's identity scan). If PR B
  adds a cache, it should be #416's design, not a second mechanism.

## Rulings and standing rules still in force

- Test first, red before green; never skip, disable or weaken a test; no empty commit to re-trigger CI.
- Implementers on sonnet (`java-jenny`), reviewers on opus (`reviewer-renata`); adversarial squads at the plan point and
  before each PR, every finding dispositioned.
- The gate is `verifyAll` (it need only pass on the final PR state). No per-target environment builds and no timing
  checks; `:packs:wholePackRoundTrip` runs at closeout only.
- Per-artifact writes run the impact check (`DM-01M6VG6GC40AKYYFRR35SV8HVW`). Hand-editing storage is unsupported: an
  operator who does it restarts the service.
- The contract is 0.x: changelog fragments, `### Breaking` where a documented response of an operation on `main`
  changes. Release hold #45: cut no tag.
- Do not take on #406, #407, #409, #411, #97 or #99.

## Working notes from PR A

- **Signing.** CLI commits are unsigned; re-sign unpushed commits before each push (check
  `git cat-file commit <sha> | grep gpgsig`; `%G?` shows `N` only because no allowed-signers file is configured).
- **Background Gradle.** Two Gradle runs in the same worktree break each other's daemon; brief implementers to run
  Gradle in the foreground only, with `--max-workers=1`.
- **History rebuild.** PR A was rebuilt as one commit per WP: each commit's tree is the approved lane tree (parallel
  lanes merged with `git merge-tree`), with `kitty-specs/` from `main`, merged with `main`'s newer commits, and a final
  status commit carrying the planning directory and the mission branch's status and review records. The final tree
  was checked against the approved lane tree.
- **The reviewer agent** was reused for every review; its notification label stayed "WP14 review" throughout. Name a
  new reviewer per phase to avoid confusing the operator.
