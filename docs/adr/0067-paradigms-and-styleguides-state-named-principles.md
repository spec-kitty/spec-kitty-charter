---
title: "ADR-0067: Paradigms and styleguides state named principles"
description: "A paradigm may state principles, and a styleguide's principles change from plain strings to the same named shape: one element, one validator, unique names, no count cap."
doc_status: active
updated: 2026-11-10T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-10'
type: ADR
kind: ADR
related:
  - adr/0022-contract-versions-start-at-0-x
  - adr/0032-steps-name-roles-and-profile-selection-order
  - adr/0064-graph-documents-are-the-only-source-of-relationships
  - adr/0065-pack-registration-replaces-the-whole-pack
  - context/conceptual-model
audience:
  - profile: pack-author
    guidance_text: Read this for the shape of a principle, where it is allowed and what makes a list of principles invalid.
  - profile: charter-service-contributor
    guidance_text: Read this before changing the principle element, its validator or the paradigm and styleguide schemas.
---

# ADR-0067: Paradigms and styleguides state named principles

## Status

**Accepted**, 2026-11-10. Decider: Stijn Dejongh (operator), on
[charter#456](https://github.com/spec-kitty/spec-kitty-charter/issues/456) (tracking
[open-packs#66](https://github.com/spec-kitty/spec-kitty-open-packs/issues/66)). The conceptual model of
[PR #199](https://github.com/spec-kitty/spec-kitty-charter/pull/199) says a paradigm states its principles, a short
list of core ideas in the style of Kent Beck's Test Desiderata. The operator also ruled that a styleguide's plain-string
principles were a lazy conversion of the as-is shape rather than a choice, and that both kinds share one shape.

## Context

- A paradigm (`paradigm.schema.json`) carries `id`, `schema_version`, `name` and `summary`. It has nowhere to state
  its principles, so open-packs cannot author them.
- A styleguide carries `principles` as a required list of plain strings. A string has no name to refer to, and the
  same word `principles` would mean two shapes if the paradigm got a richer one.
- [PR #446](https://github.com/spec-kitty/spec-kitty-charter/pull/446) set the length caps the pack schemas use: 64
  for identifiers and names, 255 for an entry.
- The charter's model is canonical and old shapes get no compatibility (charter, amended 2026-11-10).

## Decision

1. **One principle element.** A principle is a closed object `{name, description}`:
   - `name`: a short name, 1 to 64 characters, not blank (the #446 cap for names);
   - `description`: one or two sentences, 1 to 255 characters, not blank (the #446 cap for entries);
   - no other key (closed, like every pack schema).
2. **Where it is used.**
   - A **paradigm** may state `principles`: optional; when present, at least one item.
   - A **styleguide** states `principles`: required, at least one item, as before, but each item is now the
     element of decision 1. The plain-string form is retired outright, with no window (decision 6).
3. **Order is significant.** The list is kept and answered in authored order: the author ranks the ideas.
4. **No count cap.** The list has no `maxItems`. No pack schema caps a list's length, and the principles are what the
   author judges they are; review, not a number, keeps the list short.
5. **Names are unique within their list**, compared after trimming surrounding whitespace and ignoring case. JSON
   Schema cannot state that, so the reader runs the one validator for principle lists and reports a repeat as a schema
   violation of keyword `uniqueItems` at the repeated item (`/principles/<n>/name`), the way `PackDescriptorReader`
   reports a repeated `overrides` artifact. The same validator guards the domain entity, so a registration body and a
   pack file are refused by the same rule.
6. **Refusals reuse the existing codes**; no new code:
   - a pack file that breaks the shape or repeats a name makes the pack unreadable: `500 pack_unreadable` on a read,
     and a `422 validation_failed` violation at the file on a whole-pack registration (ADR-0065);
   - a per-artifact registration body that breaks it is `422 validation_failed` and nothing is written.
7. **The contract.** `Principle` is one contract schema, used by `Paradigm`, `ParadigmRegistration`, `Styleguide` and
   `StyleguideRegistration`. On a read, a paradigm's `principles` is always present, `[]` when it states none.
   The styleguide change is **Breaking** under ADR-0022 (the 0.x line may break on a minor); the paradigm field is
   **Added**.

## Consequences

### Positive

- open-packs can author paradigm principles, and a principle can be named and cited.
- One element, one validator, one contract schema: the two kinds cannot drift.

### Negative

- Every styleguide in open-packs must rewrite its principles; until it does, those packs fail to read against this
  contract. The open-packs pin moves only once they are rewritten.

### Risks

- An author writes a long list. Mitigation: review; a cap can be added later if real content needs one.

## Alternatives considered

### Cap the list at twelve items

Not chosen (operator): no other pack list is capped, and the number had no basis.

### Keep styleguide principles as strings

Not chosen (operator): the strings were carried over from the old shape, not designed; named principles make sense in
a styleguide too.

### Check name uniqueness in the domain only

Not chosen: a domain breach has no pointer into the file. Reporting it as a schema violation gives the author the
location, as the descriptor reader already does.

## References

- [charter#456](https://github.com/spec-kitty/spec-kitty-charter/issues/456),
  [open-packs#66](https://github.com/spec-kitty/spec-kitty-open-packs/issues/66)
- [PR #199](https://github.com/spec-kitty/spec-kitty-charter/pull/199), the
  [conceptual model](/context/conceptual-model/) ("Paradigms: schools of practice, adopted")
- [PR #446](https://github.com/spec-kitty/spec-kitty-charter/pull/446) (length caps)
- [ADR-0065](/adr/0065-pack-registration-replaces-the-whole-pack/) (how a registration reports a fault)
