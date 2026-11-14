# open-packs#TBD-pre-review-guard: foundation's work-package lifecycle and the `spec-kitty-pre-review` guard

> Draft for `spec-kitty/spec-kitty-open-packs`, written by charter mission `slice-f-kind-taxonomy-01M7B19K` (WP11).
> The orchestrator files it and replaces `TBD-pre-review-guard` with the issue number.

## Title

Author foundation's work-package lifecycle and its `spec-kitty-pre-review` guard on `in_progress -> for_review`

## Why

The software-dev `review` step contract carries open-packs' only gate:

```yaml
gates:
  - on_transition: "in_progress->for_review"
    handler: "spec-kitty-pre-review"
    handler_kind: "mission_step_contract"
    schema_version: "1.0"
    fail_open: true
    provenance: "built-in"
```

Charter ADR-0054 decision 12 moves it onto the foundation lifecycle's `in_progress -> for_review` transition, and
ADR-0077 decision 3 completes that move: the step contract is retired, so the gate must live as a guard on a
lifecycle. `fail_open: true` becomes `on_indeterminate: pass`. Open-packs has no lifecycle and no guard artifact
today. Core reading the guard is its own work (spec-kitty-charter #339, core, after the cutover).

## What

Add two files to foundation, and list them in its manifest (guards and lifecycles are manifest constituents in
`scripts/ci/validate_pack_schemas.py`, `MANIFEST_CONSTITUENT_SCHEMAS`).

`packs/foundation/guards/spec-kitty-pre-review.guard.yaml`:

```yaml
schema_version: '1.0'
id: spec-kitty-pre-review
title: The pre-review checks pass
description: The checks a work package runs before it moves to review. It was the gate of the software-dev review step contract (charter ADR-0054 decision 12, ADR-0077 decision 3).
interface: guard/1
implemented_by: builtin:spec-kitty-pre-review
on_indeterminate: pass
```

`builtin:spec-kitty-pre-review` is in the guard schema's closed list of built-ins, at the current pin and after
slice F.

`packs/foundation/lifecycles/work-package.lifecycle.yaml`, with core's nine displayed lanes (`specify_cli` status
model: planned, claimed, in_progress, for_review, in_review, approved, done, blocked, canceled). Its 27 transitions
are exactly the edges core's state machine allows between those lanes; only `in_progress -> for_review` carries a
guard:

```yaml
schema_version: '1.0'
id: work-package
title: Work package
description: The lanes a work package moves through, as core's status model names them. The move to review runs the pre-review checks.
initial: planned
states:
- id: planned
  title: Planned
- id: claimed
  title: Claimed
  description: An agent has taken the work package and has not started it.
- id: in_progress
  title: In progress
  description: Someone is implementing it.
- id: for_review
  title: For review
- id: in_review
  title: In review
  description: A reviewer is reviewing it.
- id: approved
  title: Approved
  description: The review passed; consolidation records it done.
- id: blocked
  title: Blocked
- id: done
  title: Done
  terminal: true
- id: canceled
  title: Canceled
  terminal: true
transitions:
- from: planned
  to: claimed
  event: claim
- from:
  - claimed
  - blocked
  to: in_progress
  event: start
- from: in_progress
  to: for_review
  guard:
  - ref: spec-kitty-pre-review
- from: for_review
  to: in_review
  event: start_review
- from:
  - in_progress
  - in_review
  to: approved
  event: approve
- from:
  - in_review
  - approved
  to: done
  event: complete
- from:
  - in_review
  - approved
  to: in_progress
  event: changes_requested
- from:
  - in_progress
  - in_review
  - approved
  to: planned
  event: reset
- from:
  - planned
  - claimed
  - in_progress
  - for_review
  - in_review
  - approved
  to: blocked
  event: block
- from: '*'
  to: canceled
  event: cancel
```

Then, in `packs/foundation/pack-manifest.yaml`, add a constituent for each file (`kind: guard`, `id:
spec-kitty-pre-review`, `path: guards/spec-kitty-pre-review.guard.yaml`; `kind: lifecycle`, `id: work-package`,
`path: lifecycles/work-package.lifecycle.yaml`) and restamp it with
`python3 scripts/ci/validate_pack_schemas.py --regenerate-manifest packs/foundation`. Restamp
`packs/foundation.source.yaml` (`pack_digest.py --update foundation`) and regenerate `docs/packs/foundation.md`.

Guards and lifecycles are not authored in graphs (`NodeKind.authoredInGraphs` is false), are in force with their
pack and are never activated, so no graph document and no preset changes.

**Open point for the open-packs author.** The charter's example pack, whose lifecycle is the reference shape, uses
six states. This sketch follows core's nine lanes instead, so that a client that maps lanes one-to-one finds every
lane. If open-packs prefers the smaller machine, keep the guarded transition as written.

This draft is valid under the current charter pin and can land before the fold. The `review` contract's `gates[]`
goes away with draft [TBD-fold-step-contracts](TBD-fold-step-contracts.md).

## Acceptance

- `scripts/ci/validate-packs.sh` passes, at the current pin and at a pin that includes slice F: both files validate
  against `guard.schema.json` and `lifecycle.schema.json`, and the manifest lists them.
- The charter service job registers foundation with the guard and the lifecycle, and the lifecycle's
  `in_progress -> for_review` transition names `spec-kitty-pre-review`.
- No `handler_kind: mission_step_contract` gate remains in open-packs.

## Files

- New: `packs/foundation/guards/spec-kitty-pre-review.guard.yaml`, `packs/foundation/lifecycles/work-package.lifecycle.yaml`
- Changed: `packs/foundation/pack-manifest.yaml`, `packs/foundation.source.yaml`, `docs/packs/foundation.md`
- Docs: the guard and lifecycle kinds in `docs/guides/author-a-charter-pack.md` (draft TBD-validator-and-docs)

## Checked here

Both sketches validate against `guard.schema.json` and `lifecycle.schema.json` of the charter build (lane through
WP10) and of the current pin `17ce3f9`. With them in a scratch copy of open-packs and the manifest restamped,
`validate_pack_schemas.py packs/foundation` and `--cross-pack` over all 19 packs report 0 violations.
