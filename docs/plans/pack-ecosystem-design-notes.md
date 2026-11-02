---
title: Pack ecosystem design notes
description: Working notes of the operator design pass on epic #142 — rulings so far, not yet formalized as ADRs.
doc_status: draft
updated: 2026-11-02T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0019-packs-resource-model-and-hypermedia
  - architecture/logical-model
audience:
  - profile: governance-lead
    guidance_text: Read this for the pack-ecosystem rulings made so far and the questions still open.
  - profile: charter-service-contributor
    guidance_text: Read this before formalizing any of these rulings; the ADR pass works from this page.
---

# Pack ecosystem design notes

Working record of the operator design pass on epic
[#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142), started 2026-11-02. These are rulings
in conversation, **not yet formalized**. The architectural, planning and debrief pass turns them into
ADRs and issues. Composition and the named-packs split are already drafted as
[ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/) (Proposed).

## 1. Composition and the split (drafted in ADR-0048)

- `depends_on: [{pack_id, range}]`. Versions are per pack. Artifact links stay unversioned, and the
  CRG closure check covers consistency. Skills keep their own `version`.
- `parent_pack` is lineage only. It must resolve to a registered pack, and the level rule applies
  (project < org < built-in).
- Packs compose into a graph that may form diamonds. A shared dependency loads once, and the version
  ranges on it must overlap. Specialist packs only add. Sibling overrides are refused unless the
  distribution pack settles them.
- Topical packs are full top-level packs (`foundational`, `writing`, `java`, `drupal`, …).
  `built-in` is a distribution pack with no artifacts of its own, and keeps the presets.
- The foundation pack keeps the shipped Mission types and base agent profiles, and is slimmed. Stack
  packs are opt-in.

## 2. Glossaries

- **Kind.** `glossary_pack` is renamed to `glossary`. The artifact is a **glossary context**, a
  ubiquitous-language bounded context modelled close to Contextive:
  - `id`, `name`;
  - `description` (its applicability, Contextive's domain vision statement);
  - `tags`;
  - optional `applies_to` (`paths` globs, `languages`, stacks once #163 lands);
  - `terms[]`.
- **Term.** An item inside a context, not an artifact on its own:
  - `id`, `name`, `definition`, `examples`, `aliases`;
  - `avoid` (synonyms not to use);
  - `status`;
  - `see_also` (term URNs).
  - Its URN is `term:<context-id>/<term-id>`, so its link to the context is structural.
  - `confidence` and `introduced_in_mission` are dropped.
- **Reads.**
  - `GET /packs/{packId}/artifacts/glossary` lists the contexts (id, name, description, tags, term
    count).
  - `GET /packs/{packId}/artifacts/glossary/{contextId}` returns one context with its terms.
  - Reading every active term across packs belongs on the active-charter side.
- **Scope** comes from the pack level. Core's four hardcoded glossary scopes are retired.
  `mission_local` stays a runtime concern on the Mission instance.
- **Clashes.** Two contexts may define the same name. Only a clash on the same `term:` URN is a
  conflict: precedence by level with a warning, and a clash at the same level is refused unless it is
  settled explicitly.
- **One source.** The pack glossary is the only authored source of vocabulary. Contextive YAML and
  the terminology gates' banned lists (fed by `avoid`) become projections of the active contexts.
  Mission types and steps link to `term:` URNs (#164's D1/Q5 ruling).
- **Core's `src/glossary`.**
  - The model and storage parts are replaced by the artifact kind, read through the seam.
  - The terminology check (`chokepoint`, `strictness`, `conflict`) stays as a consumer.
  - The learning and clarification pipeline is retired, subject to a census of what is live.
  - This is post-cutover core work.

## 3. API paths

- The `artifacts` segment in `/packs/{packId}/artifacts/{kind}/{artifactId}` **stays**
  (ADR-0019 D2). It keeps kinds apart from the other pack sub-resources, gives the cross-kind listing
  a home, and lets one route serve every kind.

## Open questions

1. The tier of our own opt-in packs (`java`, `qa`, …).
2. Foundation membership and the curation cuts (from a census).
3. Whether `built-in` aggregates any topical pack besides foundation.
4. The version-range grammar.
5. The edge form a distribution pack uses to settle a sibling conflict.
6. Whether a context's `applies_to` filters what is in force (like `applies_to_languages`), or is
   only advisory.
