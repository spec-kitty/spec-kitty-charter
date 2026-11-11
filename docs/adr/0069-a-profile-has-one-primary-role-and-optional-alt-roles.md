---
title: "ADR-0069: A profile has one primary role and optional alt-roles"
description: "role and alt-roles replace the positional roles list outright, and role fit is the first, strict key of profile selection: a primary-role match outranks every alt-role match."
doc_status: active
updated: 2026-11-10T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-10'
type: ADR
kind: ADR
related:
  - adr/0022-contract-versions-start-at-0-x
  - adr/0032-steps-name-roles-and-profile-selection-order
  - adr/0068-a-pack-declares-its-fallback-profile
  - context/conceptual-model
audience:
  - profile: pack-author
    guidance_text: Read this for how a profile declares its roles and how that decides which profile serves a step.
  - profile: contract-consumer
    guidance_text: Read this for the role fields a profile read answers and the selection order the service implements.
  - profile: charter-service-contributor
    guidance_text: Read this before changing the profile's role fields or building profile selection.
---

# ADR-0069: A profile has one primary role and optional alt-roles

## Status

**Accepted**, 2026-11-10. Decider: Stijn Dejongh (operator), on
[charter#458](https://github.com/spec-kitty/spec-kitty-charter/issues/458) (tracking
[open-packs#70](https://github.com/spec-kitty/spec-kitty-open-packs/issues/70)). It gives
[ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/) decision 2 ("one primary role and optional
alt-roles") its fields and states decision 3.1 as an explicit order. The operator ruled that the charter's model is
canonical: the new shape replaces the old one with no deprecation window.

## Context

- A profile carries `roles: [...]`; the first entry is the primary role by position only.
- Core selects with a weighted score in which role is a 10% signal, so a generic profile can beat a specialist
  ([conceptual model](/context/conceptual-model/), PR [#199](https://github.com/spec-kitty/spec-kitty-charter/pull/199)).
- Core reads a scalar `role:` as a deprecated spelling of `roles`. Core is not a consumer of this contract until the
  service is incorporated (charter, 2026-10-28); it aligns then, through the generated client.

## Decision

1. **Fields.** A profile declares:
   - `role`: its primary role. Required.
   - `alt-roles`: the other roles it can fill, in authored order. Optional; when present, at least one item, each
     once, never repeating `role`.

   A role token is kebab-case, `^[a-z][a-z0-9-]*$`, at most 64 characters (the #446 cap). The kebab keys follow the
   profile file's own convention (`profile-id`, `routing-priority`). `generic` is reserved for a fallback profile
   (ADR-0068 decision 4).
2. **`roles` is retired outright.** The profile schema is closed, so a file that still carries `roles` is unreadable
   (`500 pack_unreadable`; a `422 validation_failed` violation on a registration). There is no window and no reader of
   the old shape: open-packs migrates its profiles to stay valid, and core aligns later.
3. **The rules a schema cannot state** (an alt-role repeating `role`) run in one validator that the reader reports as
   a schema violation at the item (`/alt-roles/<n>`), so the author gets a pointer, and that also guards the entity.
4. **Selection order** (ADR-0032 decision 3, made explicit). For the role a step asks for:
   1. A role pin, when a derived pack declares one, narrows the candidates first (ADR-0032 decision 4; how a pin and
      the order combine stays ADR-0032's open question).
   2. **Role fit is the outer key, and strict.** A profile whose `role` is the asked role (*primary fit*) outranks
      every profile that lists it in `alt-roles` (*alt fit*), whatever their specialization or specificity. A profile
      that fits neither, and a profile whose role is `generic`, is not a candidate.
   3. **Within one fit tier, specialization**: the most specialized profile that fits the context, by
      `specializes_from` lineage, then by specificity (`routing-priority`, higher first).
   4. **Ties** are ADR-0032's open question, unchanged.
   5. **No candidate**: the fallback in force (ADR-0068), marked `fallback`; none: `no_profile_matched`.

   This replaces the weighted score: role is no longer a weight beside the others but decides first. A profile id is
   not accepted as a role: steps name roles, never profiles (ADR-0032 decision 1).
5. **How the result is marked.** A selection answer carries `primary`, `alt` or `fallback` (ADR-0068 decision 6).
6. **The contract.** A profile read answers `role` and `altRoles` (`[]` when none); `roles` is removed. This is
   **Breaking** under ADR-0022.

## Consequences

### Positive

- A profile says which role is its own; a specialist that only can review never beats a reviewer.
- One shape everywhere: files, model, contract, tests and examples.

### Negative

- Every profile in open-packs must be rewritten (26 in built-in); until then those packs do not read, and the
  open-packs pin moves only after.
- Today's winners change where a lower-priority primary fit lost to a higher-priority alt fit.

## Alternatives considered

### A deprecation window reading both shapes

Not chosen (operator): the old shape grew incrementally and was never designed; the model is canonical.

### Keep role as a weighted signal

Not chosen: it lets a generic profile win over a specialist, which ADR-0032 rejected.

## References

- [charter#458](https://github.com/spec-kitty/spec-kitty-charter/issues/458),
  [open-packs#70](https://github.com/spec-kitty/spec-kitty-open-packs/issues/70)
- [ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/), [ADR-0068](/adr/0068-a-pack-declares-its-fallback-profile/)
