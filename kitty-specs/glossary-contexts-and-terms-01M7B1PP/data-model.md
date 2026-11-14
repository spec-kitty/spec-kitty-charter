# Data model: R12 glossary contexts and terms

The source is ADR-0049 decisions 1 to 6, amended by ADR-0053 decisions 11 to 14. The rules for an activation are in
ADR-0076.

## Pack file: `glossaries/<context-id>.glossary.yaml` (schema `glossary`)

| Field | Required | Rule |
| --- | --- | --- |
| `schema_version` | yes | `"1.0"` |
| `id` | yes | Letter-first slug (`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64 characters), equal to the file stem |
| `name` | yes | Non-blank string |
| `description` | yes | Non-blank string: the context's applicability, which becomes Contextive's domain vision statement |
| `tags` | no | At least one non-blank string, without duplicates |
| `applies_to` | no | `{paths?, languages?, stacks?}`, each a list of at least one string. Advisory (ADR-0049 decision 14) |
| `terms` | yes | At least one term, unique by `id` |

The object is closed (`additionalProperties: false`), at every level.

### Term (an item of `terms`)

| Field | Required | Rule |
| --- | --- | --- |
| `id` | yes | Letter-first slug, at most 64 characters, unique within the context |
| `name` | yes | Non-blank: the canonical surface form |
| `definition` | yes | Non-blank string |
| `examples` | no | At least one non-blank string |
| `aliases` | no | At least one non-blank string: accepted alternative forms |
| `avoid` | no | At least one non-blank string: forms not to use |
| `status` | yes | `draft`, `active` or `deprecated` |
| `see_also` | no | At least one `term:<context-id>/<term-id>` URN |

A `see_also` URN that no held context resolves is a broken reference, reported as other unresolved references are.

## Domain (`:packs`)

- **`GlossaryContext`** (replaces `GlossaryPack`): `CharterArtifact` of kind `GLOSSARY`.
  - Fields: `id: GlossaryContextIdentifier`, `name`, `description`, `tags`, `appliesTo`, `terms: List<GlossaryTerm>`.
  - Invariants: at least one term, and term ids unique.
  - `label()` returns `name`, `urn()` returns `glossary_context:<id>`, and `term(TermIdentifier)` returns an
    `Optional<GlossaryTerm>`.
- **`GlossaryTerm`** (reshaped): `id: TermIdentifier`, `name`, `definition`, `examples`, `aliases`, `avoid`,
  `status: TermStatus`, `seeAlso: List<Urn>`. `urn(contextId)` returns `term:<context>/<id>`.
- **`GlossaryContextIdentifier`** and **`TermIdentifier`**: value types checked by
  `IdentifierGrammar.isLetterFirstSlug`.
- **`TermStatus`**: an enum of `DRAFT`, `ACTIVE` and `DEPRECATED`.
- **`AppliesTo`**: `paths`, `languages` and `stacks`, each a list.

## Kernel

- **`ArtifactKind.GLOSSARY`**: token `glossary`, plural `glossaries`, activatable.
- **`NodeKind.GLOSSARY_CONTEXT`**: token `glossary_context`, artifact kind `GLOSSARY`.
- **`NodeKind.TERM`**: token `term`, no artifact kind.
- `NodeKind` declares, per constant, its token, artifact kind, fragment plural, whether a graph may declare it as a
  node, and whether an edge may target it (research R1). `GLOSSARY_CONTEXT`'s plural is `glossaries`. `TERM` has no
  plural, is never declared, and is a `vocabulary` edge target. A dangling `term:` target is a broken reference.
- **`Relation.VOCABULARY`**: its targets are `glossary_context` and `term`.

## Contract read models (`contracts/charter/schemas`)

| Schema | Shape |
| --- | --- |
| `GlossaryContextList` | `{packId, items: [GlossaryContextSummary], _links}` |
| `GlossaryContextSummary` | `{id, name, description, tags?, termCount, _links}` |
| `GlossaryContext` | `Artifact` member `glossary`: the pack-file fields in camel case (`schemaVersion`, `appliesTo`), terms included |
| `GlossaryTerm` | `{id, name, definition, status, examples?, aliases?, avoid?, seeAlso?, urn, contextId, _links}` |
| `EffectiveGlossaryContextRead`, `EffectiveGlossaryTermRead` | The effective envelope used by the skill read: `activationId`, `revision`, `governanceFingerprint`, `kind`, `artifactId`, `packId`, `level`, `policy`, `state`, `reason`, then the context or term, then `_links` |
| `ActiveTermList` | `{activationId, revision, governanceFingerprint, terms: [ActiveTerm], _links}` |
| `ActiveTerm` | `GlossaryTerm`'s fields, plus `packId`, `level` and `_links` (`effective-term`, `effective-glossary`) |
| `TabooList` | `{activationId, revision, governanceFingerprint, entries: [{word, term, name}], _links}` |
| Contextive document | `application/yaml`: `contexts[] → {name, domainVisionStatement, paths?, terms[] → {name, definition, examples?, aliases?, meta: {urn, status}}}`. The contract describes it as a string with an example, as for `/openapi.yaml` (ADR-0076 decision 6). |

## State and precedence (an activation)

- **Context**: one winning copy per id across the closure. A higher level overrides a lower one whole, with no warning
  of its own. Same-level siblings are refused `pack_sibling_conflict` unless settled (C14, C15). The effective state is selected, deselected, denied or not
  selected, as for any activatable kind.
- **Term**: in force exactly when its context's winning copy is in force and holds it. A term has no state of its own.
- **Stored activation**: `selected.glossary` and `deactivated.glossary` hold context ids. `glossary_pack` is no longer
  a member, and is refused.
