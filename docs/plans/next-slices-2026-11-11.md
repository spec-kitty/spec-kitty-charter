---
title: Next slices after the 2026-11-11 triage
description: The next slice to run (B, with its two rulings first), the parallel slice C lane, and six quick-win slices drawn from the 2026-11-11 issue triage.
doc_status: draft
updated: 2026-11-11T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/roadmap
  - plans/milestones
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to find which slice to pick up next and which issues it holds.
  - profile: governance-lead
    guidance_text: Read this for the two rulings slice B needs before it starts.
---

# Next slices after the 2026-11-11 triage

The issue triage of 2026-11-11 left 152 open issues. Each has a type, a priority and a milestone.
This page records which slice runs next and which quick-win slices run beside it. Each slice has
an issue, and the issues it holds are native sub-issues of it where they had no other open
parent. The order of the main slices is the [roadmap](/plans/roadmap/)'s "Toward a service core
can use".

## Next: slice B, real packs register as they are (#466)

Slice B runs next, ahead of slice C, because no real pack registers unedited. Every open-packs
pack is refused with `422 unmodelled`, over its documentation files (#451) and, for several, over
`org-charter.yaml` (#431). Core can use nothing else the service serves until a real pack
registers. Slice C's spdd case and the re-run of the usability run, which is the readiness gate,
both wait on it.

Two rulings come first:

1. **Documentation files (#451).** Registration either holds them as opaque documents, skips
   them with a notice, or requires packs to leave them out. Proposed: skip with a notice. They
   carry no governance meaning, and ADR-0036 asks the round trip to keep meaning, not bytes.
2. **`org-charter.yaml` (#431).** Either (a) move its intent, org-level preset enforcement, to
   presets plus policy levels and retire the file, or (b) model the file, or (c) refuse it by
   name. Proposed: (a). It is the only option that removes a legacy shape, as the canonical-model
   rule in `charter.md` asks, and the file's retirement is then open-packs work.

Scope:

| Issue | What |
| --- | --- |
| #451 | Documentation files, as ruled |
| #431 | `org-charter.yaml`, as ruled |
| #490 | A write during a whole-pack replace can get a `500 pack_unreadable` (P2) |
| #432 | Reword the comment that says the `text` content form is refused, or close it as won't-do |
| #453 | Closed: moved to open-packs #202 (may be settled by open-packs PR #198) |

Done when a real pack registers unedited and the usability run's registration checks pass. Under
ADR-0070, that proof runs in open-packs CI against a pinned charter release.

## In parallel: slice C, the effective step and action context (#467)

Slice C touches different code from slice B and can run as a separate Mission now:

- #438: compose checks for step enhancements and the effective step prompt read. Both blockers
  (PR #448, PR #460) are merged. Re-plan it against `main`.
- #450: the scoping of required and project directives. The body-links half shipped in PR #486.

## Quick-win slices

None needs a ruling, and none touches slice B's or C's code. Each can run as its own Op or small
Mission.

| Slice | Issue | Holds | Why it is quick |
| --- | --- | --- | --- |
| A close-out | #492 | #488, #489, #443, #487; decide #104, then close #96 | Procedures shipped in PR #486; this is follow-through, and it closes slice A |
| Contract clean-up before 0.1.0 | #493 (under #468) | #411, #454; also #312, and the body rewrites of #9 and #72 | Contained changes; 0.1.0 then waits only on slices A to C |
| Green nightly runs | #494 | #461, #463, #465, #387, #388, #389 | The mutation nightly is red because two tests fail under PIT with no mutation applied; Sonar needs clicks and two rulings |
| Dead code and duplicated logic | #495 | #383, #444, #324, #134; also #184 | Behavior-preserving, with file:line evidence |
| Sharper refusals | #496 | #382, #254, #381; also #121 | Small refusals, each tested with an invalid example pack |
| Round-trip gate known failures | #497 | #476, #479 | Each fix shrinks `expected-red.yaml` |

"Also" names an issue that keeps its existing open parent (#6, #69, #142 or #226) and is listed in
the slice issue's body instead.

Suggested order: the A close-out and green nightly runs first. The first closes slice A, which slice D
needs. The second makes the nightly signal trustworthy before slices B and C land. The others can
run at any time.

## Not in these slices

- The stale issues that need a priority call: Creeds and Values (#16 and its children), the cutover
  reads R4 to R16 (#98, #104 to #110) and #112, and the verifiability backlog (#143).
- #477 (`impacts` edge strength), which touches the tension model of ADR-0028 and ADR-0045.
- The core work parked under #361.
- Contract 1.0.0 (#128) and hosting (#396), which follow slice E.
