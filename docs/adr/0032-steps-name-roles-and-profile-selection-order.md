---
title: "ADR-0032: Steps name roles, and a profile is selected by role fit, then specialization, then fallback"
description: Mission steps ask for a role, never a profile; the service picks the most specialized fitting profile, a declared fallback, or refuses; a derived pack may pin a role.
doc_status: active
updated: 2026-10-26T00:00:00Z
decision_status: Accepted
decision_date: '2026-10-26'
type: ADR
kind: ADR
related:
  - adr/0030-removal-is-distinct-from-deactivation
  - adr/0006-onion-vertical-slices-archunit
  - context/conceptual-model
audience:
  - profile: pack-author
    guidance_text: Read this for how mission steps name roles, how profiles declare roles and context, and how a fallback is declared.
  - profile: contract-consumer
    guidance_text: Read this for what the service answers when you ask for a profile, including the fallback and the not-found refusal.
  - profile: charter-service-contributor
    guidance_text: Read this for the selection order the service implements and the tie question still open.
---

# ADR-0032: Steps name roles, and a profile is selected by role fit, then specialization, then fallback

## Status

**Accepted** — 2026-10-26. Decider: Stijn Dejongh (operator), concept session of 2026-10-26
("Profiles: specialists who fill roles", "How a profile is chosen", "Selection order" and
"Pinning a role" in the [conceptual model working notes](/context/conceptual-model/)).

A new decision; it supersedes no record of this repository. Tie handling is an **open question**
(see [Open questions](#open-questions)).

## Context

Today steps in shipped packs name profiles (`agent_profile:`), not roles; the QA pack pins a
profile id on every step. Profiles carry a `roles:` list whose first entry is primary by position,
a context in `specialization-context` and a `routing-priority`. Core selects with a weighted score
where the role is a 10% signal, lineage is not ranked and ties produce one winner. That diverges
from the operator's intent on most points.

The concept session settled that roles are the only link between mission types and profiles, that
roles are extensible by packs, and an explicit selection order.

## Decision

1. **Steps name roles, never profiles.** A mission step asks for a role. The past mix of roles and
   profiles in steps is dropped. A mission type does not name profiles.
2. **A profile declares its roles and context.** One primary role and optional alt-roles, plus its
   contextual descriptors (for example language). Roles are not a closed vocabulary: packs extend
   them, and they become known when their pack is registered.
3. **Selection order:**
   1. **Role fit.** A profile whose primary role matches beats one that fills it as an alt-role.
   2. **Specialization.** Among fitting profiles, the most specialized one that fits the context
      wins: the `specializes_from` lineage plus the specificity level, which is `routing-priority`.
      The most generic profile for a role has no contextual descriptor.
   3. **Declared fallback.** When no profile fits, the service returns the fallback profile,
      marked as a fallback so the client can tell it from a real match. A pack declares its
      fallback profile. Built-in ships a role-neutral, `locked` one; a derived pack may declare a
      more specific fallback but cannot remove the built-in one. The service hard-codes only the
      rule, never fallback content.
   4. **Not found.** When no fallback is available in the context, the service refuses with a
      clear not-found message. This replaces the earlier "empty suggestion" rule. The client may
      still choose to run without a profile.
4. **A derived pack may pin the profile for a role.** Every step asking for that role gets the
   pinned profile, whatever the mission type. Profiles that specialize the pinned one
   (`specializes_from`) still fit, so an org can refine a pinned profile further. Pins live in the
   pack-root policy file ([ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/)).
5. **`operator` is a role like any other.** A profile can be marked as performed by a human; a
   step that asks for `operator` (or a pinned role) can be served by a human.
6. **Mission types and profiles are their own concepts**, each with its own domain model, loosely
   coupled to the rest by role or by id.
7. **Representation is a candidate only.** The query shape, the role value object and the
   endpoint are left to later records. No contract path changes (C-003).

## Open questions

- **Ties.** When two or more profiles are equally fit after steps 1 and 2, there is no hidden
  tie-break. Either the service returns all equal candidates and the client decides (simplest),
  or it picks one at random with a warning. Not decided.
- **Role pin and the selection order.** This record does not say how a role pin (a derived pack
  pinning the profile for a role) combines with the selection order. The reading recorded in the
  [conceptual model](/context/conceptual-model/#role-pin) and journey 08: a pin restricts the
  candidates to the pinned profile and its specializations, and is applied before the selection
  order. Not decided.

## Consequences

### Positive

- A mission type works with any pack that offers profiles for its roles.
- An org changes who does a kind of work (for example every review by a human) without editing
  any step.
- The client always knows whether it got a real match, a fallback or nothing.

### Negative

- Every shipped step that names a profile needs rewriting to a role, in `spec-kitty-open-packs`.
- Core's weighted selection differs and must change to match.

### Risks

- With only `routing-priority` and lineage to rank, ties may be common. Mitigation: the open
  question is answered before the selection is built.

## Alternatives considered

### Keep core's weighted score

Not chosen: role is a minor signal there and lineage is ignored, so a generic profile can win
over a specialist.

### Steps may name either a role or a profile

Not chosen: it couples a recipe to one pack's people; pins give orgs the control without that.

## References

- [Conceptual model working notes](/context/conceptual-model/)
- [ADR-0006](/adr/0006-onion-vertical-slices-archunit/) (planned slices for mission types and
  profiles), [ADR-0030](/adr/0030-removal-is-distinct-from-deactivation/)
