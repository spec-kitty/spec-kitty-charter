# PR C closeout notes

PR C (WP27–WP29) of mission `layer-merge-and-effective-reads-01M6R0J6`, branch `issue-10-pr-c-graph-context`, rebuilt on
`main` at `2b00e333` (runner session `session_01E5wJUBQ4VPs6CrRRu4Q8ov`, 2026-11-09).

## History

- One signed commit per WP (WP27, WP28, WP29), each a lane's code increment (`lane-y`, `lane-z`, `lane-{`) over `main`
  with `kitty-specs/` left as on `main`. The tree after WP29 was checked against the approved `lane-{` head: it differs
  only by `main`'s newer commit `2b00e333` (Sonar workflow and three files no lane touches).
- The WP28 commit message carries the out-of-map lines its review asked for (WP28 MINOR-3).
- Then the fold commits (approved WPs' NITs, WP29 cycle-2 MINOR-5/6 and NITs, the pre-merge squad's accepted findings),
  then one status commit with the planning directory.

## WP29

- Review cycle 1 rejected on MAJOR-1: the deactivate's early floor check judged only the deny entries a write adds, so a
  stored deny on a locked or no-longer-overridden target escaped. Fixed red-first; cycle 2 approved.
- A fresh container has no analysis receipt (it lives in a clone's `.git`); the implement claim of the fix cycle was
  refused. The input hashes all matched the 12:07 report, so the same analysis was re-recorded (`bf1cdc23`). Three
  `record-analysis` commits (1996ecfe, 52e759b4, bf1cdc23) stay unsigned while their receipts are needed.
- The CLI resets a WP's subtasks on rejection: mark them done again before `for_review`. `move-task` has no
  `--allow-sparse-checkout`; only `action implement` takes it.

## Rulings and decisions

- Operator ruling (decision `01M70NGH42RWKBMZ6MN2D6QHNY`): the IC-13 fragment is `### Breaking` (a documented `409
  artifact_required` succeeds with `200`/`201` when an active override satisfies the requirement, and becomes `500
  pack_unreadable` when the closure's layer merge refuses). It supersedes the runner decisions `01M70JFX…` and
  `01M70N4G…`.

## Pre-merge squad (`research/pre-pr-squad-pr-c/`)

Every finding is dispositioned in `dispositions.md`. Deferred:

- C-10: the `2026-11-07-c-*` fragment and changelog file names carry the WP files' `create_intent` date, not the writing
  date (2026-11-09). For release assembly (#45 holds the release).
- C-16: the ADR-0059 amendment label "R6" shares its name with research R6. Candidate for the next ADR-0059 amendment.
- C-17: DIV-239..244 "corpus case" evidence is core-side by the register's design.
- R-4: an `overrides` edge from a non-activatable kind onto a required artifact makes the IC-13 write and the read
  disagree. Pinned by `ClosureViewSessionStepRefsTest`; ruling needed, tracked in #447.

## Behaviour changes for the PR description

- `### Breaking`: `NodeReference` in `EnhancerWithoutTarget.targets`; `EffectiveGraph.layerOverrides`;
  `EffectiveActionContext` (`enhancements`, the extended `SkippedReport`); IC-13 (above).
- `### Changed`: the step-enhancer binding (OD-6).
- The write cascade follows only held steps (OD-9, W20).
- The effective-read tag domain is `v2`: a client holding a PR B tag gets `200` with the new body, not `304`.

## Coordination

- #437 (phase 1 of the step-enhancements mission) rewrites `CharterRefusalCode.yaml` near PR C's four hunks (main
  :27-35, :48-52, :56-60, the `x-refusal` block); whichever merges second rebases. The conflict list was sent through
  the dispatcher. `ContractDescriptionsTest`'s pin is narrowed to the removed sentence.
- #438 (phase 2) consumes `StepHolding`, `NodeReference`/`NodeRef`, `ActionContextWalk` (new public `requireKnown`) and
  `ACTION_NOT_FOUND`; the names match its `prc-symbols.md`. Post the merge notice there.
