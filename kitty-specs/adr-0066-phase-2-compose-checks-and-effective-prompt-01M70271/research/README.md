# Phase-2 mission: parked material (ADR-0066 compose checks and effective step prompt)

On 2026-11-09 the operator ruled to split the governed mission `adr-0066-step-enhancement-service-alignment-01M6ZRYK`
(branch `issue-424-428-adr-0066-service-alignment`) into two missions, one per phase:

- **Phase 1** is that mission, narrowed to WP01–WP06. It closes #428 and refers to #424.
- **Phase 2** is a new mission that the run lead creates later, on its own branch; its slug is recorded on creation.
  It closes #424. This directory holds its draft spec and the material parked for it.

Nothing here is a mission yet. Nothing here is committed.

## Blocked until

Phase 2 starts only when both have happened:

1. PR C of `layer-merge-and-effective-reads-01M6R0J6` (WP27–WP29, #429) has merged into `main`, with the OD-6 E6
   amendment (a selected step enhancer is in force exactly when the mission type of its single `enhances` target is in
   force), the OD-9 shared predicate ("P defines `mission_type:M` and declares `action:M/s`"), `NodeReference`,
   `action_not_found` and the action walk.
2. The phase-1 mission has merged.

Then the phase-2 mission **re-plans and re-tasks against `main`**. The parked work packages and the contract draft
below were written before PR C merged; they are input to `/spec-kitty.plan` and `/spec-kitty.tasks`, not a plan.

## Contents

| Path | What it is |
|---|---|
| `spec.md` | Draft spec of the phase-2 mission, in the template of the phase-1 spec. Marked "Draft — re-validate against main after PR C merges". Its IDs are new (FR-001..FR-007, NFR-001..NFR-002, C-001..C-006, SC-001..SC-004); each row cites the phase-1 spec ID it came from. |
| `parked-wps/WP07-compose-slot-declared-check.md` | Former WP07: slot facts and the `prompt_slot_missing` compose check (IC-06 slot part, IC-05 code). |
| `parked-wps/WP08-compose-target-and-integrity.md` | Former WP08: target resolution through the OD-9 predicate and inherited integrity (IC-06). |
| `parked-wps/WP09-effective-prompt-query.md` | Former WP09: the effective-prompt query in `packs` (IC-07, packs side). |
| `parked-wps/WP10-effective-prompt-read-served.md` | Former WP10: contract delta B and the read served (IC-05, IC-07). |
| `parked-wps/WP11-docs-phase-2.md` | Former WP11: docs for the compose rules and the read (IC-08, phase-2 part). |
| `parked-contracts/effective-step-prompt.openapi.yaml` | The contract draft of the read (path, four schemas, examples, the `prompt_slot_missing` example, the `x-refusal` entries, `_links.prompt`). Written before OD-5, OD-7 and OD-8 were ruled: its `<OD-n>` placeholders are now decided (see below). |
| `parked-contracts/README.md` | The phase-1 mission's contracts README as it stood before the split, including its IC-05 section. |
| `issue-phase1.md`, `issue-phase2.md` | Tracker issue bodies for the two missions. |

The parked work packages still carry the phase-1 mission's frontmatter (`planning_base_branch`, lanes, subtask IDs
T031–T051, requirement refs to the phase-1 spec's former IDs). Do not import them as they are: re-task them, with new
IDs mapped to this draft spec.

## Plan input from the phase-1 mission

Read these sections of `kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/plan.md` on branch
`issue-424-428-adr-0066-service-alignment` (or on `main` once phase 1 merges):

- "Split into two missions (operator ruling, 2026-11-09)".
- IC-05, IC-06 and IC-07, each marked "(moved to the phase-2 mission)" and kept as the record.
- IC-08, its phase-2 bullets: the ADR-0066 d8 (OD-1) and d7 (OD-2) annotations, the conceptual-model note, the rules
  page's compose rules and effective step prompt, the C-11 operation lists, the glossary term, the changelog.
- "Operator decisions (plan point, 2026-11-09): ruled" (the table) and the analysis under it.
- Technical Context (NFR-003 and NFR-004 of the phase-1 plan are this draft's NFR-001 and NFR-002), the Structure
  Decision bullets on slot facts (A7) and ordering (C-002 of the layer-merge mission), and the gates table rows for
  IC-05 to IC-07.
- "Signatures IC-05 to IC-07 assume from PR C", to pin in a compile-only test first.

Also: the phase-1 `data-model.md` sections "Composition facts added" and "EffectiveStepPrompt", `research.md` D5, D13,
D14, P5, P6, P7, and `quickstart.md` Flow 3, each marked as moved.

## Operator rulings that bind it

From the phase-1 plan's "ruled" table (2026-11-09):

- **OD-1 / D13 (B):** only an active enhancement refuses a compose whose prompt in force lacks its slot; dated
  annotation on ADR-0066 d8; contract wording narrows (`### Changed`).
- **OD-2 / D14 (A):** a step inherits its mission type's mutability level; `500 pack_unreadable` on reads,
  `422 override_not_permitted` at registration, description broadened, no new enum value; note on ADR-0066 d7.
- **OD-4 (i):** `prompt_slot_missing` and the target and integrity checks are also judged at registration and on the
  writes that rebuild the merge: `422`, with `operations: {getPackRegistration: 422}`; `### Breaking`.
- **OD-5:** new refusal code `409 mission_type_not_in_force` for a mission type held but not in force (`### Breaking`).
  The parked contract draft predates this ruling.
- **OD-6:** PR C owns the E6 amendment; a named external dependency here.
- **OD-7:** a `diagnostics` member, reusing `Diagnostic.yaml`, on the effective-prompt response (the registration half
  is phase 1's).
- **OD-8:** a slot holding legacy text is listed with no flag; the renderer keeps anything between the markers and
  adds the fragments after it.
- **OD-9:** one shared "pack holds step" predicate, implemented by PR C in WP27; FR-002 here calls it.
- **OD-10:** the D19 addendum rows are approved as a set; row 5 (`ActivationControllerTest`) and row 6's
  `prompt_slot_missing` entry are this mission's.
- **D2:** the compose checks and the read are built after PR C and reuse its action walk and `NodeReference`.

## Operator approval recorded after the split (2026-11-09)

- **OD-5 rows approved.** The new raised code `409 mission_type_not_in_force` needs one added row in the raised-code
  table of `contracts/charter/scenarios/errors.feature` and, if it is listed there, in `ReservedRefusalCodesTest`. The
  operator approved these added rows in the interview of 2026-11-09. Nothing is removed or weakened; the parked WP10
  may land them with the new code without asking again.
