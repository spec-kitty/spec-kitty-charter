---
title: "Pack schema: Glossary context"
description: "The glossary schema: a glossary context and its terms, their addresses, how a context is overridden and served, and how it maps to a Contextive file, for pack authors."
doc_status: draft
updated: 2026-11-14T00:00:00Z
type: Architecture
kind: Schematic
schematic_path: schemas/glossary.schema.json
related:
  - adr/0049-glossary-contexts-and-terms
  - adr/0076-the-activation-vocabulary-reads-and-projections
  - api/service-contract
audience:
  - profile: pack-author
    guidance_text: Read this when you write a glossary context, the vocabulary of one bounded context, in a pack.
---

# Pack schema: Glossary context

This page describes `glossaries/<id>.glossary.yaml`, the file of the artifact kind `glossary`: one glossary context,
a ubiquitous-language bounded context with its terms ([ADR-0049](/adr/0049-glossary-contexts-and-terms/)). Read it
when you write one; the diagram, the property list and the link to the schema file are generated from the schema
itself.

The kind `glossary` replaced `glossary_pack` outright on 2026-11-14
([#106](https://github.com/spec-kitty/spec-kitty-charter/issues/106)). There is no reader of the old shape: a
`glossary_packs/*.glossary-pack.yaml` file is refused by name, and a preset that names `activated_glossary_packs`
makes its pack unreadable. A preset names contexts under `activated_glossaries`, and an activation selects them under
`selected.glossary`.

## Layout and addresses

- One context per file, at `glossaries/<id>.glossary.yaml`. The `id` equals the file stem; a context elsewhere is
  refused by placement.
- A context's URN is `glossary_context:<id>`. Its URN token differs from the kind token `glossary` on purpose
  ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 13), so core's `glossary:<id>`
  is refused.
- A term is an item of its context, not an artifact. Its URN is `term:<context-id>/<term-id>`. A `term:` URN is an
  edge endpoint only as the target of a `vocabulary` edge, and a graph document never declares it as a node. An edge
  from a term, or of any other relation onto a term, is refused (operator ruling, 2026-11-14).

## Shape

The object is closed at every level.

| Field | Meaning |
|---|---|
| `schema_version` | The string `"1.0"`. |
| `id` | The context id: a letter-first kebab slug of at most 64 characters, equal to the file stem. |
| `name` | The context's display name. Not blank. |
| `description` | The context's applicability. Not blank. |
| `tags` | Optional. At least one non-blank string, without duplicates. |
| `applies_to` | Optional and advisory (ADR-0049 decision 14): `paths`, `languages` and `stacks`, each a list of at least one string. |
| `terms` | At least one term, unique by `id`. |

A term:

| Field | Meaning |
|---|---|
| `id` | A letter-first kebab slug of at most 64 characters, unique within the context. |
| `name` | The canonical surface form. Not blank. |
| `definition` | Not blank. |
| `examples` | Optional. At least one non-blank string. |
| `aliases` | Optional. Accepted alternative forms. |
| `avoid` | Optional. Forms not to use; the one source of words to avoid. |
| `status` | `draft`, `active` or `deprecated`. |
| `see_also` | Optional. At least one term reference, whose context id and term id are each a letter-first kebab slug of at most 64 characters. |

A `see_also` entry is a reference like any other (operator ruling, 2026-11-14). A bare
`term:<context-id>/<term-id>` names a term of the same pack. `<pack>::term:<context-id>/<term-id>` names a term
another pack holds, as a qualified reference. A bare entry that only another pack holds is refused as
an unqualified reference, and an entry that no held context resolves is a broken reference.

`confidence`, `introduced_in_mission`, `provenance` and `surface` are not part of the shape. `surface` became
`name`, and `synonyms_to_avoid` and `banned_synonyms` folded into `avoid`.

## Under an activation

- **Whole-context override.** A context overrides across levels whole, as every artifact does: project over org over
  built-in ([ADR-0076](/adr/0076-the-activation-vocabulary-reads-and-projections/) decision 1). Terms are not merged
  across copies, so a project that adds one term to a built-in context copies the whole context. Two copies at one
  level are refused `pack_sibling_conflict` unless settled.
- **A term has no state of its own.** It is in force when the winning copy of its context is in force and holds it
  (ADR-0076 decision 2). An override is reported by the effective reads, as for every kind; there is no
  glossary-specific load warning.
- **Every status is served.** A `draft` or `deprecated` term of a context in force is listed with its status, and a
  client filters by status (ADR-0076 decision 3).

## Contextive and the taboo list

The activation's vocabulary reads project the contexts in force
([Service contract](/api/service-contract/)). The Contextive definitions file maps one context to one Contextive
context:

| Glossary context | Contextive |
|---|---|
| `name` | `name` |
| `description` | `domainVisionStatement` |
| `applies_to.paths` | `paths` |
| a term's `name`, `definition`, `examples` and `aliases` | the same keys |
| a term's URN and `status` | `meta.urn` and `meta.status` |

The taboo list is the projection of every `avoid` entry of the terms in force: one entry per avoided word per term,
with the URN and name of the term to use instead. A terminology gate reads it instead of keeping a list of its own.
