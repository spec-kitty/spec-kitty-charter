---
title: "ADR-0030: Removal by a derived pack is distinct from deactivation by a project"
description: A derived pack may remove open and extendable artifacts pack-wide, never locked ones; deactivation stays per project; role locks and pins live in the pack-root policy file.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0012-pack-mutability-policy
  - adr/0013-persistent-deactivation
  - adr/0026-write-surface-is-functional-registration
  - adr/0032-steps-name-roles-and-profile-selection-order
  - context/conceptual-model
audience:
  - profile: governance-lead
    guidance_text: Read this for how an org pack removes artifacts it does not want, and what it cannot remove.
  - profile: pack-author
    guidance_text: Read this for what a derived pack may do to the pack it builds on, and where role locks and pins are declared.
  - profile: charter-service-contributor
    guidance_text: Read this for the operation set behind the mutability levels and the contents of the policy file.
---

# ADR-0030: Removal by a derived pack is distinct from deactivation by a project

## Status

**Accepted** — 2026-10-26. Decider: Stijn Dejongh (operator), concept session of 2026-10-26
("What a derived pack may do to the pack it builds on", "Pinning a role" and "Requirement, removal
and the operator" in the [conceptual model working notes](/context/conceptual-model/)).

Supersedes in part:

- [ADR-0012](/adr/0012-pack-mutability-policy/) decision 1 (the level table): removal is an
  operation of its own, allowed on `open` and `extendable`, refused on `locked`.
- [ADR-0012](/adr/0012-pack-mutability-policy/) decision 2 (the policy file's contents): the file
  also holds role locks and role pins.
- [ADR-0026](/adr/0026-write-surface-is-functional-registration/) decision 2: "removing an artifact
  from force is deactivation" no longer covers a derived pack's removal.

## Context

ADR-0012 gave each level three operations: override, enhance, deactivate. `extendable` allowed
enhance only. ADR-0026 said removing an artifact from force is deactivation, an activation concern.
Both treat "this artifact is gone" as one act.

The concept session separated two acts with different owners and reach:

- **Removal**: an org says "none of our projects get this". The example is "We Hate Snakes LLC",
  which removes the Python artifacts of the built-in pack from every project that uses its pack.
- **Deactivation**: one project takes an artifact off its own plate.

It also settled that a derived pack may pin the profile for a role
([ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/)), and that roles follow the
same integrity rules as artifacts: a derived pack can remove or lock them.

## Decision

1. **Removal and deactivation are two acts.**
   - **Removal** is pack-wide. A derived pack names, one artifact at a time in its own
     configuration, an artifact of a pack it builds on. The artifact is then absent from every
     project that uses the derived pack.
   - **Deactivation** is per project. It is the project's choice and persists as ADR-0013 says.
2. **What a derived pack may do to the pack it builds on**: add artifacts; change existing ones
   within their level; suggest a selection (presets); remove artifacts.
3. **Only `locked` is fully protected.** Removal of an `open` or `extendable` artifact is allowed;
   removal of a `locked` one is refused with `governance_policy_violation`.
4. **No filter-based removal.** There is no "remove everything Python". Removal is expected to be
   rare. The built-in pack is instead split into several packs and keeps a minimal offering; other
   packs are chosen at project init (relates to epic #145).
5. **Roles follow the artifact rules.** A derived pack can remove or lock a role, and can pin the
   profile that fills a role. Role locks, role pins and other integrity settings live in the
   pack-root policy file that ADR-0012 decision 2 introduced.
6. **The tighten-never-loosen rule stands** (ADR-0012 decision 3). Removal is not loosening: the
   artifact is gone, not altered.
7. **Representation is a candidate only.** The shape of a removal entry, a role lock or a role pin
   in the policy file, and how a removal reaches the write surface, are left to later records. No
   contract path changes (C-003).

## Consequences

### Positive

- An org can trim an offering it builds on without asking each project to deactivate.
- A project's deactivation keeps its meaning: one project, one choice.
- Every integrity setting of a pack, roles included, is found in one file.

### Negative

- `extendable` now allows more than ADR-0012 said: an author who wanted "keep it, only extend it"
  must use `locked`.
- The write surface of ADR-0026 needs a way to record a removal, which it does not have.

### Risks

- A removal hides an artifact a project had activated. Mitigation: the upgrade rules of
  [ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/) report items that no longer exist
  as skipped, never drop them silently.
- Authors who relied on `extendable` to block removal lose that protection on existing packs.
  Mitigation: no pack ships a policy file yet.

## Alternatives considered

### Removal is deactivation applied to every project

Not chosen: it puts an org's pack-wide act in each project's activation state, where a project
could undo it.

### Filter-based hiding

Not chosen: the operator expects removal to be rare, and splitting the built-in pack serves the
broad case better.

### Protect `extendable` from removal

Not chosen: the operator ruled that only `locked` is fully protected.

## References

- [Conceptual model working notes](/context/conceptual-model/)
- [ADR-0012](/adr/0012-pack-mutability-policy/), [ADR-0013](/adr/0013-persistent-deactivation/),
  [ADR-0026](/adr/0026-write-surface-is-functional-registration/)
- Epics [#55](https://github.com/spec-kitty/spec-kitty-charter/issues/55) and
  [#145](https://github.com/spec-kitty/spec-kitty-charter/issues/145)
