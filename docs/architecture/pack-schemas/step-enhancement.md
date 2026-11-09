---
title: "Pack schema: Step enhancement"
description: "The Step enhancement pack schema: its properties, required fields and a rendered diagram, for authors of pack files."
doc_status: draft
updated: 2026-11-08T00:00:00Z
type: Architecture
kind: Schematic
schematic_path: schemas/step-enhancement.schema.json
audience:
  - profile: pack-author
    guidance_text: Read this when you write a step enhancement that contributes prompt fragments to a mission step.
---

# Pack schema: Step enhancement

This page describes `missions/<type>/enhancements/<id>.step-enhancement.yaml`, the file by which a pack adds to a mission step ([ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/)). Read it when you write one; the diagram, the property list and the link to the schema file are generated from the schema itself.

The file names no target. The enhancing pack's graph documents carry the one `enhances` edge, to `action:<type>/<step>`. Each `prompt_fragments` entry pairs a slot declared by that step's prompt (`<namespace>:<name>`) with a [prompt fragment](/architecture/pack-schemas/prompt-fragment/) companion beside the enhancement. A preset activates the enhancement through `activated_step_enhancements`; there is no second gate for its fragments.

## Shape

| Field | Meaning |
|---|---|
| `schema_version` | The string `"1.0"`. |
| `id` | The enhancement's stable identity, equal to its file stem; at most 64 characters. |
| `prompt_fragments` | One entry per slot: `slot` (`<namespace>:<name>`) and `path` (the companion `<stem>.prompt-fragment.md` beside this file). At least one entry. |

The shape has no target, range, anchor, position or priority field. Guard and emit additions are fields too, not edges
(`add_guards`, `add_lifecycle_guards` and `emits`), but they are not in this shape yet: they are built under
[#434](https://github.com/spec-kitty/spec-kitty-charter/issues/434) ([ADR-0066 annotation of 2026-11-09](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/#annotation-2026-11-09-guards-and-emits-are-fields-d6)).

## Layout

The file lives at `missions/<type>/enhancements/<id>.step-enhancement.yaml`, per mission type, with its
`*.prompt-fragment.md` companions beside it ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/), open question 2, annotated 2026-11-09).
The `<type>` directory must equal the type of the enhancement's one `enhances` edge; it is checked against the edge and
never read instead of it. An id is unique in its pack across the type directories. The service writes a pack back with
each enhancement at the place it was read.

## Rules and codes

A pack that breaks one of these is refused when it is validated, registered or read. The full rules, moments and order
are on [Load rules](/architecture/composition-and-activation-rules/#load-rules).

| Fault | Answer |
|---|---|
| `id` differs from the file stem; a `slot` breaks `<namespace>:<name>`; `prompt_fragments: []` | A schema violation. |
| A slot named twice; a companion missing, shared by two entries or named by none (an orphan) | `prompt_fragment_invalid`. |
| No `prompt_fragments` key | `prompt_fragment_invalid`. |
| Not exactly one `enhances` edge to an `action:<type>/<step>`; any `overrides` edge from the enhancement; a `<type>` directory that differs from the edge's type | `prompt_fragment_invalid`. |
| The id repeated across type directories | `prompt_fragment_invalid` at the second file in path order, which is withheld from the pack. |
| A fragment that is blank, has frontmatter or holds a slot marker | `prompt_fragment_invalid` at the fragment. |

At registration the answer is a violation `{pointer, code}` inside `422 validation_failed`, one for each fault; on a
composing read it is `500 pack_unreadable` naming the first faulty file. Two packs at one level that define the same
enhancement id are refused `pack_sibling_conflict` unless a descriptor `overrides` entry settles it.
Checks made when packs compose, such as a slot the target prompt does not declare, are planned in the phase-2 mission
([#438](https://github.com/spec-kitty/spec-kitty-charter/issues/438)).
