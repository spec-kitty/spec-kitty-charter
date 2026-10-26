---
title: "ADR-0031: Requirement is authored as a preset, and the required set is derived from it"
description: An org states what must be on the plate through a preset's must-have items; the service derives the required set; two open questions stay recorded.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0012-pack-mutability-policy
  - adr/0013-persistent-deactivation
  - adr/0020-offering-and-activations
  - context/conceptual-model
audience:
  - profile: governance-lead
    guidance_text: Read this for how you declare what every project must activate, and the questions still open.
  - profile: pack-author
    guidance_text: Read this for the two jobs a preset has and how a required set is written once.
  - profile: core-maintainer
    guidance_text: Read this for what happens to required_* lists and the open compatibility question.
---

# ADR-0031: Requirement is authored as a preset, and the required set is derived from it

## Status

**Accepted** — 2026-10-26. Decider: Stijn Dejongh (operator), concept session of 2026-10-26
("Three independent levers", "Presets have two jobs" and "Requirement, removal and the operator"
in the [conceptual model working notes](/context/conceptual-model/)).

Supersedes in part [ADR-0012](/adr/0012-pack-mutability-policy/) decision 7: `required_*` is no
longer the authoring form of a requirement. What "required" means (presence only, deactivation
refused) is unchanged.

Two questions are **open** (see [Open questions](#open-questions)).

## Context

Today an org declares requirements as `required_<plural>` lists in `org-charter.yaml`. Presets
are a separate, pack-shipped convenience: a one-shot selection written into a project's active
charter. ADR-0012 decision 7 defined required as presence only, and refused its deactivation.

The concept session settled three independent levers (requirement, integrity, and the project's
own selection) and two jobs for a preset:

- **Governor**: an org states what must be on the plate.
- **Convenience**: an author offers sane defaults ("don't want to customize? take these").

It ruled that requirement is declared through a preset that includes the non-optional
selections, written one way.

## Decision

1. **Requirement is authored as a preset.** A preset may mark items as must-have. Those items
   are the required set.
2. **The service derives the required set from the preset.** The required list is computed, not
   authored a second time.
3. **What required means is unchanged.** A required artifact is always active; deactivating it
   is refused locally in open-core, naming the requiring pack (ADR-0012 decision 7, ADR-0013
   decision 5). Its content is as open as its level says: requirement and integrity stay
   independent.
4. **Prohibition is not a separate mechanism.** It is a requirement phrased inversely, for
   example a required, `locked` directive that says what to avoid.
5. **Cross-repository policing is out of scope.** The service declares and honours requirement
   per project; checking that every repository of an org complies belongs to org CI or a
   commercial package.
6. **Representation is a candidate only.** How a must-have item is marked in a preset file is
   left to a later record. No contract path changes (C-003).

## Open questions

- **Compatibility reading of `required_*`.** Whether the service keeps reading existing
  `required_<plural>` lists (for example as a preset it derives from them), for how long, and
  how a pack that has both is treated. Not decided.
- **Telling a governing preset from a convenience preset.** How the service, a project and a
  reader tell a preset that governs (must-have items) from one that only offers defaults: a
  marker on the preset, on each item, or the presence of any must-have item. Not decided.

## Consequences

### Positive

- One idea, written one way: an org's "must take" and its suggested selection are the same
  kind of thing.
- A reader finds an org's requirements where its selections already are.

### Negative

- Existing `required_*` lists need a migration or a compatibility reading (open question).
- Presets gain a second meaning; a reader must tell the two apart (open question).

### Risks

- Applying a convenience preset could be mistaken for accepting a requirement. Mitigation: the
  second open question is answered before the representation is built.

## Alternatives considered

### Keep `required_*` as the authoring form

Not chosen: it states the same selection twice, once as a preset and once as a list.

### A separate requirement file

Not chosen: it adds a third place an org's intent lives.

## References

- [Conceptual model working notes](/context/conceptual-model/)
- [ADR-0012](/adr/0012-pack-mutability-policy/), [ADR-0013](/adr/0013-persistent-deactivation/),
  [ADR-0020](/adr/0020-offering-and-activations/)
- Core ADR [`4.x/2026-10-06-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-06-2-charter-offering-active-charter-and-activation-presets.md)
