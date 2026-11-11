---
title: Author a procedure
description: "The checklist for a procedure pack artifact: what loading the pack already refuses, and the three refusals of ADR-0052 that review applies."
doc_status: draft
updated: 2026-11-02T00:00:00Z
type: Guide
kind: How-To
related:
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
  - architecture/descriptors
  - architecture/pack-schemas/procedure
  - architecture/logical-model
audience:
  - profile: pack-author
    guidance_text: Follow this before you write a procedure, to check that it is one, and when you review one.
  - profile: governance-lead
    guidance_text: Use the checklist when you review a procedure in a pack pull request.
---

# Author a procedure

This page is for writing or reviewing a `*.procedure.yaml` file in a Charter Pack. A procedure has to pass two
kinds of check:

- loading the pack checks the shape;
- a reviewer checks what the procedure is, against the checklist below.

The checklist is the one [ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/)
decision 3 asks for, and that decision has review apply it. The operator confirmed on 2026-11-02 that no CI check
applies it ([#150](https://github.com/spec-kitty/spec-kitty-charter/issues/150)).

## What a procedure is

A procedure is **a repeatable, bounded episode used inside one step** (ADR-0052 decision 2). Three refusals tighten
that definition:

| A procedure may not… | If it does, it is… |
|---|---|
| produce the Mission's deliverables | a mission type, or a step |
| span steps (start in one step, end in another) | a mission type, a step's actions, or a lifecycle |
| be a rule (a standing obligation such as "at least once per release") | a directive, with tactics for the technique |

## Steps

1. Check the three refusals in the table against what you mean to write. If one applies, write a directive, a
   lifecycle, a mission type or a step instead.
2. Create `procedures/<id>.procedure.yaml` in the pack. `id` is kebab-case, equal to the file stem, and at most 64
   characters. The [descriptors](/architecture/descriptors/#procedure--procedureyaml) page lists the fields.
3. Fix anything loading refuses (next section).
4. Walk the [review checklist](#the-review-checklist), as the author, and again as the reviewer of the pack pull
   request.

## What loading already refuses

The shape is
[contracts/packs/schemas/procedure.schema.json](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/packs/schemas/procedure.schema.json),
the sanitized shape of [ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) decision 2.
Review does not need to look for any of the following, because loading the pack refuses them:

- **An undeclared key, at any level.** The root, each step, each reference and each inline anti-pattern entry are
  closed (`additionalProperties: false`). The schema has no output field, so an `outputs` or `deliverable` key is
  refused as unknown. The procedure binding declares no output template either; a mission type is the kind that
  does.
- **A missing entry or exit condition.** `entry_condition` and `exit_condition` are required non-empty strings.
  `steps` needs at least one step, and each step needs a non-empty `title`.
- **A reference of type `action`.** A reference's `type` must be one of the artifact kinds the schema lists, and
  `action` and `mission_type` are not among them.
- **An id that is not the file stem.** The schema checks the kebab-case pattern and the 64-character cap. The
  loader also checks that the id equals the file stem.

A load skips a file that breaks any of these and records a violation, so the rest of the pack still loads. In
validate mode (`FileSystemPackInspector`, the seam for the pack doctor) the finding is `artifact_schema_error`. In
CI, the corpus tests of `:packs:test` round-trip every procedure in charter's example packs
(`service/packs/src/testFixtures/resources/example-packs`, ADR-0070) as part of `verifyAll`. It checks schema validity and round-trip fidelity only.

## What loading cannot see

ADR-0052 decision 3 says the schema "admits no `action:` reference". That is true of a reference's `type`, but not
of its `id`. `Reference.id` is free text, so `{type: procedure, id: "action:software-dev/plan"}` validates today.
Tightening that is [#350](https://github.com/spec-kitty/spec-kitty-charter/issues/350). Until it lands, review
checks it.

The three refusals are judgment calls on free text: the description, the conditions and the steps. No load check
reads them.

## The review checklist

Check each point against the file. One "no" means the file is not a procedure. Move its content to what the table
above names instead: a directive, a lifecycle, a mission type or a step.

1. **One step.** Can the whole procedure run inside one step of a mission type? Its `entry_condition` and
   `exit_condition` must both hold within that step. If the exit is reached in a later step, or a step names a
   hand-off to a later step, it spans steps.
2. **No deliverable.** Is the result a state of the work ("the defect is reproduced and fixed"), not a Mission
   deliverable? Read `exit_condition`, `description` and every step's `on_success`. If it ends in a report, a
   canvas, a specification, a plan, or pack artifacts the Mission hands over, it is a mission type or a step.
3. **Not a rule.** Is it a sequence someone runs when the entry condition holds, not an obligation that holds all
   the time? "At least once per release", "always", "every Mission must" and policy wording point to a directive,
   with tactics for the technique.
4. **Repeatable and bounded.** Could it run again under the same entry condition, and does it stop at the exit
   condition? An open-ended loop or a one-off is not a procedure.
5. **No step reference by id.** No `references[].id` is an `action:` URN, or any other step address, until
   [#350](https://github.com/spec-kitty/spec-kitty-charter/issues/350) makes the schema refuse it.

The shipped procedures, sorted against these refusals, are a worked example. ADR-0052 decisions 5 to 7 list them,
as amended by [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 29.
`test-first-bug-fixing` and `refactoring` stay procedures. These do not:

- `spike-timebox-policy` is a rule, so it becomes a directive.
- `issue-triage-state-machine` spans steps, so it becomes a lifecycle.
- `situational-assessment` and `legacy-codebase-triage` end in deliverables, so curation re-homes them.

## Anti-patterns

Write anti-patterns inline in `anti_patterns` for now. ADR-0052 replaces them with a `rejects` list, but that is
decided and not built ([descriptors](/architecture/descriptors/#procedure--procedureyaml)).

## See also

- [Pack schema: Procedure](/architecture/pack-schemas/procedure/): the generated property list and diagram.
- [Logical model](/architecture/logical-model/): where the procedure kind sits among the artifact kinds.
