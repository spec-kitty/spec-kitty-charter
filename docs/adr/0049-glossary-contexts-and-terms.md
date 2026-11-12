---
title: "ADR-0049: Glossary contexts and terms: one authored source of vocabulary"
description: "glossary_pack becomes glossary: an artifact is a glossary context of terms addressed as term:<context>/<term>, scoped by pack level, the one source of vocabulary."
doc_status: active
updated: 2026-11-12T00:00:00Z
decision_status: 'Accepted, with Amendment 2026-11-02 (ADR-0053); decision 7 amended, decision 8 extended by ADR-0074 (2026-11-12)'
decision_date: '2026-11-02'
type: ADR
kind: ADR
related:
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0039-a-kind-binding-can-carry-companion-files
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - adr/0029-charter-glossary-is-owned-here
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - plans/pack-ecosystem-work-breakdown
  - architecture/logical-model
  - plans/pack-ecosystem-design-notes
audience:
  - profile: pack-author
    guidance_text: Read this for how a pack declares its vocabulary as glossary contexts and terms, and how clashes between packs are settled.
  - profile: charter-service-contributor
    guidance_text: Read this before renaming the glossary kind, writing the glossary schema or adding the context reads.
  - profile: core-maintainer
    guidance_text: Read this for what replaces core's glossary model, scopes and storage, and what core keeps (the terminology check).
---

# ADR-0049: Glossary contexts and terms: one authored source of vocabulary

## Status

**Accepted**, 2026-11-02. Decider: Stijn Dejongh (operator). Formalizes the operator rulings of 2026-11-02
recorded in the [pack ecosystem design notes](/plans/pack-ecosystem-design-notes/) (section 2, "Glossaries", and
section 3, "API paths"), on epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142).

It **supersedes in part** these Accepted records, and each carries a "superseded in part by ADR-0049"
note in its `decision_status`:

- [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/) decision 9, where the kind token "stays
  `glossary_pack`", the file keeps its authored path whatever its stem, and a new one is created as
  `<id>.glossary-pack.yaml`; and decision 8's per-kind row (`**/*.glossary-pack.yaml`, `confidence` a number, a
  repeated `surface` a domain violation);
- [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/)'s amendment of 2026-10-27 (#190), for the
  glossary schema only ("no schema file is renamed");
- [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) R15 (DIV-012), for the URN kind set,
  which gains `glossary` and `term`;
- [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) decision 2 (R12), where a glossary pack's terms are
  artifacts of kind `glossary`. Under this record the contexts are the artifacts and the terms are items inside
  them, and R12's `?kind=glossary` listing becomes a per-kind list read
  (`GET /packs/{packId}/artifacts/glossary`, decision 7). *(Amended 2026-11-12 ([ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decision 9): `?kind=glossary` is not replaced; it lists the contexts beside the per-kind list.)* The `artifacts` path segment of the same decision is re-affirmed (decision 9).

### Amendment 2026-11-02

The operator ruled every open question on 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decisions 11 to 14). One ruling changes a decision of
this record: the glossary context URN token is `glossary_context`, not `glossary` (decision 4), so the seam does not
wait for core to retire its own `glossary` node kind. The original text stands; the changed point is annotated.

## Context

- **Today's shape is core's.** `glossary_packs/<id>.glossary-pack.yaml` holds `{id, description, provenance,
  terms[]}`; a term is `{surface, definition, status, confidence, aliases?, see_also?, synonyms_to_avoid?,
  introduced_in_mission?}`; the pack schema (`contracts/packs/schemas/glossary-pack.schema.json`) also allows
  `banned_synonyms?`. The census of spec-kitty-open-packs (`1060bfc`) finds one built-in glossary,
  `spec-kitty-core`, with 129 terms (103 active, 12 draft, 14 deprecated; 12 carry `synonyms_to_avoid`), and
  glossaries in qa (19 terms), event-driven (13) and internal (11).
- **A term has no address.** It is not a CRG node, so a mission type or step cannot link to the term it relies on
  (#164's D1/Q5 ruling asks for that).
- **Vocabulary is authored in several places.** Core has four hardcoded glossary scopes and a learning and
  clarification pipeline; the docs site has its own Contextive file; the terminology gates keep their own banned
  lists. They drift.
- **Contextive** ([contextive.tech](https://contextive.tech/)) models vocabulary per bounded context: a context
  has a name, a domain vision statement, and terms with definitions, examples and aliases. That is the model the
  operator chose to stay close to.

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
### The kind and its artifact

1. **`glossary_pack` is renamed `glossary`** (kind token `glossary`, operator token `glossary`). Its artifact is a
   **glossary context**: a ubiquitous-language bounded context.
2. **The glossary context shape** (closed, per the sanitized-shape rules of
   [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/)):

   | Field | Required | Shape |
   |---|---|---|
   | `schema_version` | yes | `"1.0"` |
   | `id` | yes | kebab-case, the file stem |
   | `name` | yes | string |
   | `description` | yes | its applicability (Contextive's domain vision statement) |
   | `tags` | no | strings, at least one |
   | `applies_to` | no | `{paths?, languages?, stacks?}`: path globs, language tokens, and stack tokens once #163 lands |
   | `terms` | yes | term items (below), at least one |

3. **A term is an item inside a context, not an artifact of its own:**

   | Field | Required | Shape |
   |---|---|---|
   | `id` | yes | kebab-case, unique within the context |
   | `name` | yes | the canonical surface form |
   | `definition` | yes | string |
   | `examples` | no | strings |
   | `aliases` | no | accepted alternative forms |
   | `avoid` | no | synonyms not to use |
   | `status` | yes | `draft`, `active` or `deprecated` (the three values the shipped terms use) |
   | `see_also` | no | term URNs |

4. **A term's URN is `term:<context-id>/<term-id>`**, so its link to its context is structural. `glossary:<id>` is
   the context's URN. Both join the CRG's URN kind set; `term` is a node-only kind, like `action`, with no file of
   its own. The `vocabulary` relation targets `glossary` and `term` instead of `glossary_pack`. Core still has a
   retiring `glossary` node kind with another meaning; the seam carries charter `glossary:` URNs only once that one
   is gone (open question 3). Mission types and steps link to the terms they rely on
   through `term:` URNs (#164 D1/Q5;
   [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) decision 26).
   *Amended 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 13): a glossary context's URN is `glossary_context:<id>`; term URNs are unchanged. The wait on core's retiring `glossary` node kind no longer applies.*
5. **Dropped:** `confidence` and `introduced_in_mission` (ruled), and `provenance` (not in the ruled shape; it
   is the pack's history, which the pack's own version and repository hold). `surface` becomes `name` and
   `synonyms_to_avoid` becomes `avoid`. The glossary pack schema's `banned_synonyms` folds into `avoid` as well, so
   `avoid` is the one source of banned vocabulary (decision 15 and the work breakdown's C3).
6. **Layout** (proposed): `glossaries/<context-id>.glossary.yaml`. The migration turns each existing glossary pack
   into one context with the same id, by the service's own writer, then a re-pin (as ADR-0043 and ADR-0044 did).
   *Ruled 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 11): this layout is adopted.*

### Reads (ruled)

7. `GET /packs/{packId}/artifacts/glossary` lists the pack's contexts: id, name, description, tags and term count.
   *Amended 2026-11-12 ([ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decisions 9 to 12): `?kind=glossary`
   is not replaced; it lists the contexts beside this list, and assets and skills get the same per-kind list. The context item gets an
   operation of its own, a term is read at `…/artifacts/glossary/{contextId}/terms/{termId}`, and each read has an
   activation twin.*
8. `GET /packs/{packId}/artifacts/glossary/{contextId}` returns one context with its terms.
9. **The `artifacts` segment stays** in `/packs/{packId}/artifacts/{kind}/{artifactId}` (ADR-0019 decision 2). It
   keeps kinds apart from the other pack sub-resources, gives the cross-kind listing a home, and lets one route
   serve every kind.
10. **Reading every active term across packs belongs on the active-charter side**, as a read of the activation, not
    of a pack. Its path and shape are left to the contract set that adds it.

### Scope and clashes (ruled)

11. **Scope comes from the pack level** (built-in, org, project). Core's four hardcoded glossary scopes are retired.
    `mission_local` stays a runtime concern on the Mission instance; it is not a charter scope.
12. **Two glossary contexts may define the same name.** A name shared by two glossary contexts is two terms, and is no conflict.
13. **Only a clash on the same `term:` URN is a conflict.** Across levels, precedence follows the level (project
    over org over built-in) and the load warns. At the same level the compose is refused unless it is settled
    explicitly, the same way as a sibling conflict (ADR-0048 decision 9).
14. **`applies_to` is advisory** (the notes' default, open question 6): it tells a reader or a tool where the
    context applies, and does not filter what is in force. Filtering, like `applies_to_languages`, can be added
    later without a shape change.

### One source (ruled)

15. **The pack glossary is the only authored source of vocabulary.** The Contextive YAML a client or a docs site
    uses, and the terminology gates' banned lists (fed by `avoid`), become **projections** of the active contexts.
    This repository's own docs glossary ([ADR-0029](/adr/0029-charter-glossary-is-owned-here/)) is not a
    pack glossary and is out of scope here.
16. **A glossary `avoid` entry is not an anti-pattern** (the #154 ruling AP4,
    [ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/)): `avoid` is about words,
    an anti-pattern about practice.

### Core's `src/glossary` (ruled; post-cutover core work)

17. Core's glossary model and storage are replaced by this kind, read through the seam. The terminology check
    (`chokepoint`, `strictness`, `conflict`) stays as a consumer. The learning and clarification pipeline is retired,
    subject to a census of what is live (the core follow-up list in the
    [work breakdown](/plans/pack-ecosystem-work-breakdown/)).

<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- A term has an address, so mission types, steps and anti-patterns can link to vocabulary.
- One authored source: the gates and the editor plug-in can no longer drift from the packs.
- Packs can bring their own bounded contexts without fighting over common words.

### Negative

- A breaking rename of a kind token, a schema file and a file family, with a migration of four glossaries.
- Term ids must be minted for 172 existing terms (from their surface form), and kept stable afterwards.

### Risks

- A projection that lags behind the packs is a second source by accident. The projections are generated, never
  hand-edited.

### Follow-ups

Nothing below is applied in this record.

- **Pack schemas** (#114): `glossary.schema.json` replaces `glossary-pack.schema.json`. Every place that spells
  `glossary_pack` changes: the reference `type` enums of the directive, tactic, paradigm, procedure and
  mission-step-contract schemas; `graph-shard.schema.json` (the URN alternation, the kind enum and its per-kind
  branch); `org-fragment.schema.json` (URN patterns and the fragment plural `glossary_packs`);
  `pack-manifest.schema.json`; `preset.schema.json` (`activated_kinds`); `charter.schema.json`
  (`selected_glossary_packs`, `activated_glossary_packs`); `org-charter.schema.json` (`required_glossary_packs`);
  `file-kinds.yaml` (node, artifact and fragment kind lists, and the `**/*.glossary-pack.yaml` rule). `term` joins
  the node kinds and the URN alternations.
- **Contract, additive** ([ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/)): the two
  context reads and the active-terms read.
- **Contract, breaking on a 0.x minor** (a `### Breaking` entry, ADR-0022): the token rename in `NodeKind`,
  `ActivatableKind`, `CascadeKind`, `SelectableKind`, `SelectedArtifacts`, `DeactivatedArtifacts` and
  `ActivationConfig`; the `glossary` member of the closed `Artifact` `oneOf` with the context representation.
- **Stored state.** A stored activation holding `selected.glossary_pack` or `deactivated.glossary_pack` fails the
  closed objects after the rename, and its `sha256:` revision changes; the rename needs a stored-activation
  migration.
- **Kernel**: `ArtifactKind`'s `glossary_pack` entry.
- **spec-kitty-open-packs**: migrate the four glossaries; the 14 deprecated built-in terms are kept with
  `status: deprecated` or cut in curation.

## Open questions

This record is accepted; the questions below still await an operator ruling, each with a recommended default.

*Ruled 2026-11-02 ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/)): every question below is ruled; each ruling follows its question.*

1. **File layout** (decision 6): `glossaries/<context-id>.glossary.yaml`, one context per file.
   **Ruled 2026-11-02:** `glossaries/<context-id>.glossary.yaml` ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 11).
2. **`applies_to`** (decision 14, the notes' question 6): advisory.
   **Ruled 2026-11-02:** advisory ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 12).
3. **The `glossary:` URN** (decision 4): keep `glossary:<id>` for a context, and gate the seam on core retiring its
   own `glossary` node kind. The alternative is a distinct token (`glossary_context:`), which avoids the wait at the
   cost of a longer name.
   **Ruled 2026-11-02:** a new token, `glossary_context:<id>` ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 13).
4. **Term `status` values** (decision 3): `draft`, `active`, `deprecated`.
   **Ruled 2026-11-02:** `draft`, `active`, `deprecated` ([ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 14).

## Alternatives considered

### Terms as artifacts of their own

Rejected. A term is meaningless outside its context; making each a file multiplies files by about 170 and loses
the context's description.

### Keep `glossary_pack` and add fields

Rejected. The artifact is a context, not a pack; keeping the name would repeat the "pack" confusion
ADR-0039 decision 9 already had to explain.

### Keep core's four scopes

Rejected. They duplicate the pack level and cannot be extended by a pack.

## References

- [Pack ecosystem design notes](/plans/pack-ecosystem-design-notes/), sections 2 and 3.
- Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142); #163 (stack scope); #164 (requirement
  kinds, D1/Q5).
- [Contextive](https://contextive.tech/) definitions file format.
- E. Evans, *Domain-Driven Design*, Addison-Wesley, 2003: bounded context and ubiquitous language.
