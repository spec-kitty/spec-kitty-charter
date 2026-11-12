---
okf_version: "0.2"
title: Next slices after the 2026-11-12 slice review
description: Where slices A to E stand after slice B and ADR-0074, and the slices that follow C and D (B close-out, A2, F, G) in their approved order.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/roadmap
  - plans/next-slices-2026-11-11
  - plans/milestones
  - adr/0074-the-remaining-cutover-reads-and-their-paths
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to find which slice runs after C and D, and which issues each one holds.
  - profile: governance-lead
    guidance_text: Read this for the rulings the next slices need, and the release ruling of 2026-11-12.
---

# Next slices after the 2026-11-12 slice review

On 2026-11-12 a planning Op (planner profile, `01M78GFNS4DY0J1E5XMPAVJNQC`) reviewed the slices of the
[roadmap](/plans/roadmap/) against `main` at `1d244f3` and the tracker. Since the
[2026-11-11 plan](/plans/next-slices-2026-11-11/), slice B landed (PR #501), the six quick wins closed, and
[ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) (PR #529) decided the remaining cutover
reads. The operator approved the slices below and their order on 2026-11-12.

## Where the slices stand

| Slice | State | What is left |
| --- | --- | --- |
| **A. Every kind is readable** | Done (#492, PRs #486, #500, #504). ADR-0074 added four reads to it, tracked as **A2** (#536) | See A2 below |
| **B. Real packs register as they are** (#466) | Built (PR #501, ADR-0073) | #110, and the proof once open-packs #241 and #242 land; see B close-out |
| **C. The effective step and action context** (#467) | In flight, as its own Mission | #438, #450, #97, #439, #447 |
| **D. Release machinery and clean-up** (#468) | Mostly done: #454, #312 (PR #507), #406 (PR #481), #9 and #72 (PR #527), #47 | #196, #45, #367 (21 methods left after PR #525), #371 |
| **E. Core ingests** (#13) | Not started; core's work | #13, #70, #452 |

Three items the 2026-11-11 plan still listed under slice B were already closed, and need no slice:

- **#453, language-tag aliasing.** There is no alias. The fix was pack data: open-packs #202, settled by
  open-packs #243 (java 2.1.0 uses language tags instead of `jvm`). No charter example pack uses `jvm`.
- **#405 and #435, the open-packs pin bumps.** #405 was fixed in open-packs content (open-packs #131).
  #435 was overtaken by [ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/): charter's tests read
  charter's own example packs, and open-packs proves its content against a pinned charter release. What is
  left of a pin is `openPacksRef`, which syncs `packs/internal` and `packs/qa` for this repository's own
  governance; it moves in the B close-out.

### The release is not tied to slices

The operator ruled on 2026-11-12 that a contract release does not wait for any slice: the operator cuts a
0.x release when what the contract offers is to their liking. Slice D's "done when" no longer says "carries slices A
to C"; it closes with its release machinery (#196, #45) and its clean-up (#367, #371).

## The next slices, in order

Slice C and slice D run first. After them, in this order:

### 1. B close-out (#466), small, whenever open-packs is ready

| Issue | What |
| --- | --- |
| #110 | Manifest membership and restamping on every write (ADR-0074 decision 7; an ADR-0042 amendment when built) |
| #509 | The nightly drift watch turns green against open-packs `main` and closes itself |
| — | Raise `openPacksRef` and `packs/open-packs.lock.yaml` to that commit; re-run the usability run's registration checks |

- **Why now:** it finishes the slice whose code has landed, and gives the usability run its readiness gate.
- **Depends on:** open-packs #241 (each pack's documentation is its `README.md`) and #242 (`org-charter.yaml`
  becomes an activation preset). Until they land, the drift watch stays red. #110 does not wait for them.
- **Size:** S. **Risk:** open-packs content that ADR-0073 refuses beyond those two issues; the drift report
  names it, and the fix is filed in open-packs.

### 2. A2: the reads ADR-0074 added (#536), large

| Issue | What |
| --- | --- |
| #98 | R4: `listPackPresets`, `getPackPreset` (P1, first) |
| #105 | R11: `asset` as a served kind, with a `payload` sub-resource |
| #107 | R13: `skill` as a served kind; its body is the `text` companion |
| #532 | The references read gets its activation twin (ADR-0074 decision 12) |
| #409 | Activations are discoverable by links |
| #106 | R12: `glossary` in ADR-0049's shape, replacing `glossary-pack` outright (last) |

- **Why now:** these are cutover reads core makes today, decided but not served. Presets are P1, and a guard
  names its implementation as `asset:<id>`, so a client that runs a guard needs the asset read.
- **Depends on:** nothing open. Open-packs follows the `glossary` shape once #106 lands.
- **Size:** L: six item operations in two trees, plus the payload and term reads.
- **Risks:** #106 is a model change of its own, so it goes last and may split into its own Mission. The
  operations widen the contract; that is allowed in a 0.x minor.

### 3. F: the kind taxonomy and the mission-type schema (#537), medium to large

| Issue | What |
| --- | --- |
| #112 | R18: kind taxonomy facts, decide and shape |
| #514 | Retire the `mission_step_contract` kind (moved from core #3422) |
| #520 | The mission-type schema: steps are the single authoring surface (moved from core #2721) |
| #515 | Rule on the reference-enum vocabulary (moved from core #2976) |
| #533 | The step-contract read under an activation: moot once #514 lands, or reshaped by it |

- **Why now:** R18 is one of the last undecided reads in epic #94, and core's scope migration moved these
  model rulings here. They change one model (which kinds exist, and how steps are authored), so they land
  together.
- **Depends on:** A2, so the taxonomy includes `asset`, `skill` and `glossary`. Open-packs follows the
  schema; the drift watch shows when it has.
- **Size:** M to L. **Risks:** #515 needs an operator ruling first. Retiring a kind removes contract
  operations, which is a `### Breaking` CHANGELOG entry in a 0.x minor.

### 4. G: agent profile resolution (#538), medium

| Issue | What |
| --- | --- |
| #102 | R8: agent profile resolution and lineage, decide and shape |
| #42 | Select an agent profile by role (ADR-0069's order) |
| #512 | A profile performed by a human serves an `operator` step (replaces `sentinel` outright) |
| #380 | Refuse an `enhances` merge that drops fields |

- **Why now:** R8 is the other undecided read in epic #94, and ADR-0068 and ADR-0069 have decided the
  fallback profile and the roles.
- **Depends on:** F (steps and their roles come from the mission-type schema). Open-packs retires
  `human-in-charge` (open-packs #139) once #512 lands.
- **Size:** M. **Risk:** ADR-0032's two open questions (ties, and how a role pin combines with the order)
  need operator rulings before #42 is planned.

### After G

- **#108, R14: the model-task routing catalog**, as a Mission of its own, with the build ADR of ADR-0074
  decision 8 first. It moves content core owns today into a pack, so open-packs follows.
- With R3 (slice C), R8 (G), R14 and R18 (F) done, epic #94 closes, and every read core makes has a contract
  operation. Slice E (core ingests) is core's.

## Where the issues moved from core belong

The scope migration moved #512 to #520, #528 and #535 here. Each now has a slice or an epic:

| Issue | Home |
| --- | --- |
| #512 | Slice G (#538) |
| #514, #515, #520 | Slice F (#537) |
| #513 (nested steps), #516 (statement authority), #518 (glossary evaluator), #519 (reference edges for project artifacts), #535 (file-name collision across layers) | Epic #142, Post-cutover |
| #517 (org pack install lifecycle), #528 (custom mission distribution, Backlog) | Epic #143, Post-cutover |

The follow-ups filed on 2026-11-12 are placed too: #532 in A2 and #533 in F (both lose the `follow-up`
label, since they are now scheduled); #530 and #531 (mutation-testing timing) under #494; #509 (the drift
watch) under #466; #371 under #468; #439 and #447 carry `slice:C`, as slice C covers them.

No open issue was found delivered or duplicated beyond those already closed on 2026-11-11 and 2026-11-12.

## Not in these slices

- The open-packs content issues, which are filed in open-packs: #241, #242, and the epics #190, #207, #244
  and #245.
- The core work parked under #361, and slice E.
- Contract 1.0.0 (#128) and hosting (#127, #396), which follow slice E.
- The stale issues the 2026-11-11 plan named: Creeds and Values (#16), the verifiability epic (#143) and
  #477.
