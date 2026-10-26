---
title: "ADR-0029: This repository owns the charter glossary"
description: The charter glossary is authored here, in site/.contextive/definitions.yaml; the import from the core glossary is retired and doc-kitty still generates the pages.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0003-docs-follow-doc-kitty
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - context/conceptual-model
  - guides/docsite
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add or change a glossary term, and for where the wording is decided.
  - profile: pack-author
    guidance_text: Read this for which glossary is the authority for charter vocabulary.
---

# ADR-0029: This repository owns the charter glossary

## Status

**Accepted** — 2026-10-26. Decider: Stijn Dejongh (operator), concept session of 2026-10-26
("Decisions for the documentation mission" in the
[conceptual model working notes](/context/conceptual-model/)).

Clarifies [ADR-0003](/adr/0003-docs-follow-doc-kitty/), which set up the generated Glossary
section; its decision stands.

## Context

The [Glossary](/glossary/) section was copied from the core repository's glossary
(`docs/context/charter.md`, `governance.md` and `product-lines.md` in `spec-kitty/spec-kitty`)
by an import script, and core stayed the authority for the wording. The concept sessions of
2026-10-26 reshaped the charter vocabulary: roles, the fallback profile, role pins, removal as
distinct from deactivation, requirement authored as a preset, ethos and creed, value impact and
the `tension` relationship. Core does not carry these terms, and importing again would erase
them. [ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/) already makes
this repository canonical for charter structure; the vocabulary that names that structure
belongs with it.

## Decision

1. **This repository owns the charter glossary.** `site/.contextive/definitions.yaml` is the
   authored source of the Charter, Governance and Product lines contexts. Terms are added and
   changed there, by pull request, like any other doc.
2. **The core import is retired.** `site/tools/import_core_glossary.py` is deleted. The terms it
   imported stay as the starting content and are edited here from now on.
3. **The pages stay generated.** doc-kitty's glossary generator still writes
   `docs/glossary/**/README.md` from the definitions file; those pages are not hand-edited, and
   the generated-page exemptions of the docs checks stay.
4. **Superseded terms are kept, marked.** A term the design replaces keeps its entry with
   `status: deprecated` and a pointer to its replacement, so older ADRs and core's vocabulary
   still resolve. A non-word identifier of a superseded term may become an alias of its
   replacement; a plain word does not.
5. **No core change now.** Core's copy of these terms is cleaned up in a later documentation
   effort in `spec-kitty/spec-kitty`.

## Consequences

### Positive

- The glossary can follow the conceptual model the day a concept is decided.
- One place decides charter wording; the docs and the glossary no longer disagree with core
  over terms core does not have.

### Negative

- Until core's documentation effort lands, core's glossary and this one differ on the renamed
  and new terms. The `deprecated` entries here name the old wording to bridge the gap.
- The section loses its automatic refresh from core: a term core adds later reaches this
  glossary only when someone adds it here.

## References

- [Building the docsite](/guides/docsite/), section "Glossary"
- [Conceptual model](/context/conceptual-model/)
